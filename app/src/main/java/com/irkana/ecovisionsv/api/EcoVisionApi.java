package com.irkana.ecovisionsv.api;

import com.irkana.ecovisionsv.model.RespuestaHealth;
import com.irkana.ecovisionsv.model.RespuestaPrediccion;

import okhttp3.MultipartBody;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Query;

/**
 * Detección y estado, a través del backend (BACKEND_BASE_URL).
 * Contrato: ecovision_admin/docs/openapi.yaml.
 */
public interface EcoVisionApi {

    /**
     * GET /api/v1/health (sin token)
     * {"status":"ok", ..., "inferencia":"lista|despertando|no_disponible|sin_configurar"}.
     * Responde en ~2 s y despierta el modelo. Indicador de conexión de MainActivity.
     */
    @GET("api/v1/health")
    Call<RespuestaHealth> health();

    /**
     * POST /api/v1/predict (Bearer de Firebase, lo añade AuthInterceptor)
     * Envía la imagen redimensionada a 640×640 como multipart; el backend la reenvía a la
     * inferencia y devuelve su respuesta sin cambios.
     *
     * @param imagen  parte multipart con la imagen JPEG (campo "file")
     * @param umbral  umbral de confianza mínimo (score_threshold de la config, 0.3–1)
     */
    @Multipart
    @POST("api/v1/predict")
    Call<RespuestaPrediccion> predict(
            @Part MultipartBody.Part imagen,
            @Query("umbral") float umbral
    );
}
