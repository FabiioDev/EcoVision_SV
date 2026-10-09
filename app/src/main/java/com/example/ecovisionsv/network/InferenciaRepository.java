package com.example.ecovisionsv.network;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import com.example.ecovisionsv.database.DeteccionDao;
import com.example.ecovisionsv.database.DeteccionEntity;
import com.example.ecovisionsv.database.EcoVisionDatabase;
import com.example.ecovisionsv.model.Deteccion;
import com.example.ecovisionsv.model.RespuestaPrediccion;
import com.example.ecovisionsv.utils.ImageResizer;
import com.google.gson.Gson;
import com.example.ecovisionsv.utils.BitmapSaver;

import java.io.IOException;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Response;

/**
 * Repositorio que orquesta el flujo completo de inferencia:
 * Redimensiona la imagen a 640×640 en un hilo de fondo (ImageResizer).
 * Envía la imagen al servidor Cloud Run vía Retrofit.
 * Aplica NMS (IoU) para eliminar detecciones solapadas.
 * Guarda el resultado en SQLite (Room) en otro hilo de fondo.
 * Notifica al caller en el hilo principal mediante {@link Callback}.
 */
public class InferenciaRepository {

    /**
     * Umbral NMS: detecciones con IoU >= 0.45 se consideran el mismo objeto.
     */
    private static final float NMS_THRESHOLD = 0.45f;
    /**
     * Umbral de confianza mínima enviado al servidor.
     */
    private static final float SCORE_THRESHOLD = 0.4f;

    public interface Callback {
        void onExito(RespuestaPrediccion respuesta, List<Deteccion> deteccionesFiltradas);

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
    public void inferir(Uri imageUri, Callback callback) {
        // redimensionar en hilo de fondo para no bloquear la UI
        EcoVisionDatabase.executor.execute(() -> {
            byte[] imagenBytes;
            try {
                imagenBytes = ImageResizer.resizeToBytes(context, imageUri);
            } catch (IOException e) {
                notificarError(callback, "Error al procesar la imagen: " + e.getMessage());
                return;
            }

            // construir el multipart y llamar a la API (Retrofit usa su propio hilo)
            RequestBody body = RequestBody.create(MediaType.parse("image/jpeg"), imagenBytes);
            MultipartBody.Part parte = MultipartBody.Part.createFormData("file", "imagen.jpg", body);

            RetrofitClient.getApi().predict(parte, SCORE_THRESHOLD).enqueue(new retrofit2.Callback<RespuestaPrediccion>() {
                @Override
                public void onResponse(Call<RespuestaPrediccion> call, Response<RespuestaPrediccion> response) {
                    if (!response.isSuccessful() || response.body() == null) {
                        int code = response.code();
                        notificarError(callback, "Error del servidor (" + code + ")");
                        return;
                    }

                    RespuestaPrediccion respuesta = response.body();

                    // filtrar detecciones solapadas con NMS
                    List<Deteccion> filtradas = respuesta.deteccionesFiltradas(NMS_THRESHOLD);

                    // persistir en SQLite en hilo de fondo
                    EcoVisionDatabase.executor.execute(() -> guardarEnBd(respuesta, filtradas, imageUri));

                    // notificar en hilo principal
                    mainHandler.post(() -> callback.onExito(respuesta, filtradas));
                }

                @Override
                public void onFailure(Call<RespuestaPrediccion> call, Throwable t) {
                    notificarError(callback, "Sin conexión o servidor no disponible.");
                }
            });
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
    private void notificarError(Callback callback, String mensaje) {
        mainHandler.post(() -> callback.onError(mensaje));
    }

    private android.graphics.Bitmap cargarBitmapCorregido(Uri uri) {
        android.graphics.Bitmap bmp;
        try (java.io.InputStream in = context.getContentResolver().openInputStream(uri)) {
            bmp = android.graphics.BitmapFactory.decodeStream(in);
        } catch (java.io.IOException e) { return null; }
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
        } catch (java.io.IOException ignored) { }

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
