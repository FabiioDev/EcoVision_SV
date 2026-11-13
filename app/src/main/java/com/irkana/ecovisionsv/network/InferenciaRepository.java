package com.irkana.ecovisionsv.network;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import com.irkana.ecovisionsv.R;
import com.irkana.ecovisionsv.config.AppConfig;
import com.irkana.ecovisionsv.config.ConfigRepository;
import com.irkana.ecovisionsv.database.DeteccionDao;
import com.irkana.ecovisionsv.database.DeteccionEntity;
import com.irkana.ecovisionsv.database.EcoVisionDatabase;
import com.irkana.ecovisionsv.model.Deteccion;
import com.irkana.ecovisionsv.model.RespuestaPrediccion;
import com.irkana.ecovisionsv.utils.ImageResizer;
import com.google.gson.Gson;
import com.irkana.ecovisionsv.utils.BitmapSaver;
import com.irkana.ecovisionsv.utils.GaleriaHelper;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Response;

/**
 * Repositorio que orquesta el flujo completo de inferencia:
 * Redimensiona la imagen a 640×640 en un hilo de fondo (ImageResizer).
 * Envía la imagen al backend (POST /api/v1/predict, con el token de Firebase), que la
 * reenvía al modelo. Los errores del backend se muestran en español (ErrorDeteccion).
 * Filtra las detecciones solapadas con el NMS de la config (por defecto IoU > 0.3) y las
 * ordena por confianza; guarda la respuesta ya filtrada.
 * Guarda el resultado en SQLite (Room) en otro hilo de fondo.
 * Notifica al caller en el hilo principal mediante {@link Callback}.
 */
public class InferenciaRepository {

    public interface Callback {
        /**
         * @param imageUri URI persistente de la imagen (puede diferir de la recibida
         *                 si venía del Photo Picker y se copió a la galería)
         */
        void onExito(Uri imageUri, RespuestaPrediccion respuesta, List<Deteccion> deteccionesFiltradas);

        void onError(String mensaje);
    }

    private final Context context;
    private final DeteccionDao dao;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public InferenciaRepository(Context context) {
        this.context = context.getApplicationContext();
        this.dao = EcoVisionDatabase.getInstance(this.context).deteccionDao();
    }

    /**
     * Lanza el proceso completo de inferencia para la imagen indicada.
     *
     * @param imageUri URI de la imagen (de cámara o galería, ya guardada en MediaStore)
     * @param callback resultado en el hilo principal
     */
    public void inferir(Uri uriRecibida, Callback callback) {
        // copiar/redimensionar en hilo de fondo para no bloquear la UI
        EcoVisionDatabase.executor.execute(() -> {
            // Config vigente al detectar: umbral, NMS, máximo de detecciones y guardado
            AppConfig cfg = ConfigRepository.getInstance(context).get();
            Uri imageUri;
            byte[] imagenBytes;
            try {
                // Las URIs del Photo Picker caducan al cerrar la app: se guarda una copia en
                // la galería. Con save_to_gallery_enabled=false no se guarda ninguna copia:
                // la URI solo se usa para enviar la imagen y mostrar el resultado.
                imageUri = !cfg.isSaveToGalleryEnabled() || GaleriaHelper.esUriPersistente(uriRecibida)
                        ? uriRecibida
                        : GaleriaHelper.copiarAGaleria(context, uriRecibida);
                imagenBytes = ImageResizer.resizeToBytes(context, imageUri);
            } catch (IOException | SecurityException e) {
                notificarError(callback, context.getString(R.string.error_procesar_imagen));
                return;
            }

            // construir el multipart y llamar a la API (Retrofit usa su propio hilo)
            RequestBody body = RequestBody.create(MediaType.parse("image/jpeg"), imagenBytes);
            MultipartBody.Part parte = MultipartBody.Part.createFormData("file", "imagen.jpg", body);

            try {
                com.irkana.ecovisionsv.api.EcoVisionApi api = RetrofitClient.getApi();
                if (api == null) {
                    notificarError(callback, context.getString(R.string.error_sin_conexion));
                    return;
                }
                api.predict(parte, cfg.getScoreThreshold()).enqueue(new retrofit2.Callback<RespuestaPrediccion>() {
                    /** Reintentos ya hechos; como máximo cfg.getRetries() (defecto 0). */
                    private int reintentos = 0;

                    @Override
                    public void onResponse(Call<RespuestaPrediccion> call, Response<RespuestaPrediccion> response) {
                        if (!response.isSuccessful() || response.body() == null) {
                            ErrorDeteccion error = ErrorDeteccion.desdeRespuesta(
                                    response.code(), cuerpoDeError(response));
                            if (error.tipo == ErrorDeteccion.Tipo.NO_DISPONIBLE) {
                                // Probablemente mantenimiento: se pide la config para que
                                // Inicio muestre el aviso sin esperar al siguiente refresco
                                ConfigRepository.getInstance(context).refrescarAhora(null);
                            }
                            notificarError(callback, mensaje(error));
                            return;
                        }

                        // Se filtra una sola vez y se guarda ya filtrada (filtrado_app)
                        RespuestaPrediccion respuesta = response.body()
                                .filtrar(cfg.isNmsEnabled(), cfg.getNmsIou(), cfg.getMaxDetections());

                        List<Deteccion> filtradas = respuesta.deteccionesFiltradas();

                        // persistir en SQLite en hilo de fondo, si la config lo permite
                        // (history_save_enabled; ya resuelve history_enabled y save_to_gallery_enabled)
                        if (cfg.isHistorySaveEnabled()) {
                            EcoVisionDatabase.executor.execute(() -> guardarEnBd(respuesta, filtradas, imageUri));
                        }

                        // notificar en hilo principal
                        mainHandler.post(() -> callback.onExito(imageUri, respuesta, filtradas));
                    }

                    @Override
                    public void onFailure(Call<RespuestaPrediccion> call, Throwable t) {
                        // Solo se reintenta ante fallo de red/timeout, no ante errores HTTP
                        if (!call.isCanceled() && reintentos < cfg.getRetries()) {
                            reintentos++;
                            call.clone().enqueue(this);
                            return;
                        }
                        // La app esperó request_timeout_s sin respuesta: el modelo va lento
                        notificarError(callback, t instanceof SocketTimeoutException
                                ? mensaje(ErrorDeteccion.timeout())
                                : context.getString(R.string.error_sin_conexion));
                    }
                });
            } catch (Exception e) {
                notificarError(callback, context.getString(R.string.error_sin_conexion));
            }
        });
    }

    // Persistencia
    private void guardarEnBd(RespuestaPrediccion respuesta, List<Deteccion> filtradas, Uri imageUri) {
        Uri bboxUri = null;
        try {
            android.graphics.Bitmap bmp = cargarBitmapCorregido(imageUri);
            if (bmp != null) {
                android.graphics.Bitmap conBboxes = dibujarBboxesSimple(
                        bmp.copy(android.graphics.Bitmap.Config.ARGB_8888, true),
                        filtradas);
                bboxUri = BitmapSaver.guardar(context, conBboxes);
                conBboxes.recycle();
                bmp.recycle();
            }
        } catch (Exception ignored) {
            // Si falla el guardado del bbox, continuamos sin él
        }

        DeteccionEntity entidad = new DeteccionEntity();
        entidad.imagenUri = imageUri.toString();
        entidad.resultadoJson = new Gson().toJson(respuesta);
        entidad.anchoImagen = respuesta.ancho;
        entidad.altoImagen = respuesta.alto;
        entidad.latenciaMs = respuesta.latenciaMs;
        entidad.timestamp = System.currentTimeMillis();
        entidad.numDetecciones = filtradas.size();

        if (!filtradas.isEmpty()) {
            Deteccion principal = filtradas.get(0); // ya ordenadas por confianza desc
            entidad.categoriaPrincipal = principal.categoria;
            entidad.confianzaPrincipal = principal.confianza;
        } else {
            entidad.categoriaPrincipal = "Sin detección";
            entidad.confianzaPrincipal = 0f;
        }
        dao.insertar(entidad);
    }

    // Helpers

    /** Texto en español para un error de /api/v1/predict. */
    private String mensaje(ErrorDeteccion error) {
        if (error.detalle != null) return error.detalle; // ya en español (backend)
        switch (error.tipo) {
            case SESION: return context.getString(R.string.error_sesion);
            case CUENTA_DESACTIVADA: return context.getString(R.string.auth_error_usuario_desactivado);
            case LIMITE: return context.getString(R.string.error_limite);
            case NO_DISPONIBLE: return context.getString(R.string.error_no_disponible);
            case IMAGEN_GRANDE: return context.getString(R.string.error_imagen_grande);
            case FORMATO: return context.getString(R.string.error_formato);
            case IMAGEN_RECHAZADA: return context.getString(R.string.error_imagen_rechazada);
            case MODELO: return context.getString(R.string.error_modelo);
            case MODELO_LENTO: return context.getString(R.string.error_modelo_lento);
            case SERVIDOR:
            default:
                return context.getString(R.string.error_servidor, error.codigo);
        }
    }

    private static String cuerpoDeError(Response<?> response) {
        try (okhttp3.ResponseBody body = response.errorBody()) {
            return body != null ? body.string() : null;
        } catch (IOException e) {
            return null;
        }
    }

    private void notificarError(Callback callback, String mensaje) {
        mainHandler.post(() -> callback.onError(mensaje));
    }

    private android.graphics.Bitmap cargarBitmapCorregido(Uri uri) {
        android.graphics.Bitmap bmp;
        try (java.io.InputStream in = context.getContentResolver().openInputStream(uri)) {
            bmp = android.graphics.BitmapFactory.decodeStream(in);
        } catch (java.io.IOException | SecurityException e) { return null; }
        if (bmp == null) return null;

        int rotation = 0;
        try (java.io.InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in != null) {
                androidx.exifinterface.media.ExifInterface exif =
                        new androidx.exifinterface.media.ExifInterface(in);
                int o = exif.getAttributeInt(
                        androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                        androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL);
                switch (o) {
                    case androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90:
                        rotation = 90; break;
                    case androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180:
                        rotation = 180; break;
                    case androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270:
                        rotation = 270; break;
                }
            }
        } catch (java.io.IOException | SecurityException ignored) { }

        if (rotation == 0) return bmp;
        android.graphics.Matrix m = new android.graphics.Matrix();
        m.postRotate(rotation);
        android.graphics.Bitmap rotated = android.graphics.Bitmap.createBitmap(
                bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
        bmp.recycle();
        return rotated;
    }

    private android.graphics.Bitmap dibujarBboxesSimple(
            android.graphics.Bitmap bitmap, List<Deteccion> detecciones) {

        int[] COLORS = {0xFF26A641, 0xFF0A84FF, 0xFFFF9F0A, 0xFF32ADE6,
                0xFFFF375F, 0xFFBF5AF2, 0xFFFF6961, 0xFF30D158};

        android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
        int w = bitmap.getWidth(), h = bitmap.getHeight();

        android.graphics.Paint border = new android.graphics.Paint(
                android.graphics.Paint.ANTI_ALIAS_FLAG);
        border.setStyle(android.graphics.Paint.Style.STROKE);
        border.setStrokeWidth(3f);

        android.graphics.Paint fill = new android.graphics.Paint(
                android.graphics.Paint.ANTI_ALIAS_FLAG);
        fill.setStyle(android.graphics.Paint.Style.FILL);

        android.graphics.Paint textBg = new android.graphics.Paint(
                android.graphics.Paint.ANTI_ALIAS_FLAG);
        textBg.setStyle(android.graphics.Paint.Style.FILL);

        android.graphics.Paint text = new android.graphics.Paint(
                android.graphics.Paint.ANTI_ALIAS_FLAG);
        text.setColor(android.graphics.Color.WHITE);
        text.setTextSize(28f);
        text.setFakeBoldText(true);

        for (int i = 0; i < detecciones.size(); i++) {
            Deteccion d = detecciones.get(i);
            if (d.bboxNorm == null || d.bboxNorm.size() < 4) continue;
            int color = COLORS[i % COLORS.length];
            float xmin = d.bboxNorm.get(0)*w, ymin = d.bboxNorm.get(1)*h;
            float xmax = d.bboxNorm.get(2)*w, ymax = d.bboxNorm.get(3)*h;
            android.graphics.RectF rect = new android.graphics.RectF(xmin,ymin,xmax,ymax);

            fill.setColor(color & 0x00FFFFFF | 0x40000000);
            canvas.drawRoundRect(rect, 12f, 12f, fill);
            border.setColor(color);
            canvas.drawRoundRect(rect, 12f, 12f, border);

            String lbl = "[" + (i+1) + "] " + d.categoria
                    + " " + Math.round(d.confianza * 100) + "%";
            float tw = text.measureText(lbl), lh = 36f;
            textBg.setColor(color);
            canvas.drawRoundRect(new android.graphics.RectF(
                    xmin, ymin, xmin+tw+16f, ymin+lh), 6f, 6f, textBg);
            canvas.drawText(lbl, xmin+8f, ymin+lh-8f, text);
        }
        return bitmap;
    }
}
