package com.irkana.ecovisionsv.api;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;

/**
 * API del backend de EcoVision (BuildConfig.BACKEND_BASE_URL).
 * Contrato: ecovision_admin/docs/openapi.yaml.
 */
public interface BackendApi {

    /**
     * GET /api/v1/config
     * Config completa de la app: {"config_version", "updated_at", "config": {...}}.
     * Con If-None-Match igual al ETag guardado, responde 304 sin cuerpo si no cambió.
     * El cuerpo se lee en crudo y lo interpreta {@link com.irkana.ecovisionsv.config.ConfigResponse}.
     *
     * @param ifNoneMatch ETag de la última config guardada, o null
     */
    @GET("api/v1/config")
    Call<ResponseBody> config(@Header("If-None-Match") String ifNoneMatch);

    /**
     * GET /api/v1/config/public (sin autenticación)
     * Lo que la app necesita antes del login: mantenimiento y, cuando el backend lo
     * añada, registration_enabled. Lo interpreta {@link com.irkana.ecovisionsv.config.ConfigPublica}.
     */
    @GET("api/v1/config/public")
    Call<ResponseBody> configPublica();
}
