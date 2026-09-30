package com.example.ecovisionsv.api;

import com.example.ecovisionsv.model.RespuestaHealth;
import com.example.ecovisionsv.model.RespuestaPrediccion;

import okhttp3.MultipartBody;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Query;

public interface EcoVisionApi {

    /**
     * GET /health
     * Devuelve el estado del servicio y la lista de clases disponibles.
     * Se usa para el indicador de conexión en MainActivity.
     */
    @GET("health")
    Call<RespuestaHealth> health();

    /**
     * POST /predict
     * Envía la imagen redimensionada a 640×640 como multipart y recibe
     * las detecciones del modelo EfficientDet D1.
     *
     * @param imagen  parte multipart con la imagen JPEG (campo "file")
     * @param umbral  umbral de confianza mínimo, por defecto 0.4
     */
    @Multipart
    @POST("predict")
    Call<RespuestaPrediccion> predict(
            @Part MultipartBody.Part imagen,
            @Query("umbral") float umbral
    );
}
