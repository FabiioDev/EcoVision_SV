package com.irkana.ecovisionsv.network;

import com.irkana.ecovisionsv.BuildConfig;
import com.irkana.ecovisionsv.api.EcoVisionApi;
import com.irkana.ecovisionsv.auth.AuthRepository;
import com.irkana.ecovisionsv.config.ConfigRepository;

import android.util.Log;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.util.concurrent.TimeUnit;

/**
 * Singleton que proporciona la instancia de {@link EcoVisionApi} (detección y estado).
 *
 * Desde la Fase 4 la app solo habla con el backend: la URL es BuildConfig.BACKEND_BASE_URL
 * (local.properties o variable de entorno BACKEND_BASE_URL al compilar; nunca en el
 * código), la misma que usa {@link BackendClient} para la config. El backend reenvía la
 * imagen al servicio de inferencia, que ya no se llama directamente.
 * Si la URL falta o no es válida, {@link #getApi()} devuelve null y quien llama lo trata
 * como "sin conexión".
 *
 * Cliente separado de {@link BackendClient} por los timeouts: /predict puede tardar
 * (arranque en frío del modelo), la config no.
 */
public class RetrofitClient {
    private static final String TAG = "RetrofitClient";

    private static final int CONNECT_TIMEOUT_S = 15;
    private static final int WRITE_TIMEOUT_S   = 30;
    /** /api/v1/health responde en ~2 s (el backend no espera al modelo). */
    private static final int HEALTH_TIMEOUT_S  = 8;

    private static volatile EcoVisionApi INSTANCE;
    private static volatile boolean construido = false;

    private RetrofitClient() {}

    /** Devuelve la API, o null si BACKEND_BASE_URL falta o no es válida. */
    public static EcoVisionApi getApi() {
        if (!construido) {
            synchronized (RetrofitClient.class) {
                if (!construido) {
                    INSTANCE = buildApi();
                    construido = true;
                }
            }
        }
        return INSTANCE;
    }

    /**
     * Añade https:// si falta el esquema y la barra final que exige Retrofit.
     * Devuelve null si la URL está vacía. Compartido con {@link BackendClient}.
     */
    static String normalizarUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.trim().isEmpty()) {
            return null;
        }
        String formattedUrl = rawUrl.trim();
        if (!formattedUrl.startsWith("http://") && !formattedUrl.startsWith("https://")) {
            formattedUrl = "https://" + formattedUrl;
        }
        if (!formattedUrl.endsWith("/")) {
            formattedUrl = formattedUrl + "/";
        }
        return formattedUrl;
    }

    /**
     * Espera máxima de la respuesta (s): request_timeout_s de la config para /predict (el
     * backend espera a la inferencia 3 s menos y responde antes); corta para /health.
     */
    static int timeoutLecturaS(String ruta, int requestTimeoutS) {
        return ruta.endsWith("/api/v1/health") ? HEALTH_TIMEOUT_S : requestTimeoutS;
    }

    private static EcoVisionApi buildApi() {
        String baseUrl = normalizarUrl(BuildConfig.BACKEND_BASE_URL);
        if (baseUrl == null) {
            Log.e(TAG, "BACKEND_BASE_URL no está configurada (local.properties o variable de entorno)");
            return null;
        }

        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        // Solo cabeceras y solo en debug (nunca el cuerpo: es la imagen)
        logging.setLevel(BuildConfig.DEBUG
                ? HttpLoggingInterceptor.Level.HEADERS
                : HttpLoggingInterceptor.Level.NONE);
        logging.redactHeader("Authorization");

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
                .writeTimeout(WRITE_TIMEOUT_S,  TimeUnit.SECONDS)
                .addInterceptor(chain -> chain
                        .withReadTimeout(timeoutLecturaS(chain.request().url().encodedPath(),
                                        ConfigRepository.configActual().getRequestTimeoutS()),
                                TimeUnit.SECONDS)
                        .proceed(chain.request()))
                // Token de Firebase en /predict (las rutas públicas salen sin él)
                .addInterceptor(new AuthInterceptor(forzar -> {
                    try {
                        return AuthRepository.getInstance().tokenBloqueante(forzar);
                    } catch (IllegalStateException e) {
                        return null; // hilo principal: la petición sale sin token
                    }
                }))
                .addInterceptor(logging)
                .build();

        try {
            return new Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(EcoVisionApi.class);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "BACKEND_BASE_URL no es una URL válida", e);
            return null;
        }
    }
}
