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

import java.io.IOException;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Response;

/**
 * Repositorio que orquesta el flujo completo de inferencia:
 *
 * Redimensiona la imagen a 640×640 en un hilo de fondo (ImageResizer).
 * Envía la imagen al servidor Cloud Run vía Retrofit.
 * Aplica NMS (IoU) para eliminar detecciones solapadas.
 * Guarda el resultado en SQLite (Room) en otro hilo de fondo.
 * Notifica al caller en el hilo principal mediante {@link Callback}.
 */
public class InferenciaRepository {

    /** Umbral NMS: detecciones con IoU >= 0.45 se consideran el mismo objeto. */
    private static final float NMS_THRESHOLD    = 0.45f;
    /** Umbral de confianza mínima enviado al servidor. */
    private static final float SCORE_THRESHOLD  = 0.4f;

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
     * @param imageUri  URI de la imagen (de cámara o galería, ya guardada en MediaStore)
     * @param callback  resultado en el hilo principal
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
            RequestBody body = RequestBody.create(
                    MediaType.parse("image/jpeg"), imagenBytes);
            MultipartBody.Part parte = MultipartBody.Part.createFormData(
                    "file", "imagen.jpg", body);

            RetrofitClient.getApi()
                    .predict(parte, SCORE_THRESHOLD)
                    .enqueue(new retrofit2.Callback<RespuestaPrediccion>() {
                        @Override
                        public void onResponse(Call<RespuestaPrediccion> call,
                                               Response<RespuestaPrediccion> response) {
                            if (!response.isSuccessful() || response.body() == null) {
                                int code = response.code();
                                notificarError(callback,
                                        "Error del servidor (" + code + ")");
                                return;
                            }

                            RespuestaPrediccion respuesta = response.body();

                            // filtrar detecciones solapadas con NMS
                            List<Deteccion> filtradas =
                                    respuesta.deteccionesFiltradas(NMS_THRESHOLD);

                            // persistir en SQLite en hilo de fondo
                            EcoVisionDatabase.executor.execute(() ->
                                    guardarEnBd(respuesta, filtradas, imageUri));

                            // notificar en hilo principal
                            mainHandler.post(() ->
                                    callback.onExito(respuesta, filtradas));
                        }

                        @Override
                        public void onFailure(Call<RespuestaPrediccion> call, Throwable t) {
                            notificarError(callback,
                                    "Sin conexión o servidor no disponible.");
                        }
                    });
        });
    }

    // Persistencia
    private void guardarEnBd(RespuestaPrediccion respuesta,
                             List<Deteccion> filtradas,
                             Uri imageUri) {
        DeteccionEntity entidad = new DeteccionEntity();
        entidad.imagenUri = imageUri.toString();
        entidad.resultadoJson = new Gson().toJson(respuesta);
        entidad.anchoImagen = respuesta.ancho;
        entidad.altoImagen  = respuesta.alto;
        entidad.latenciaMs  = respuesta.latenciaMs;
        entidad.timestamp   = System.currentTimeMillis();
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
}
