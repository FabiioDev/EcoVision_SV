package com.example.ecovisionsv.network;

import com.example.ecovisionsv.BuildConfig;
import com.example.ecovisionsv.api.EcoVisionApi;
import com.example.ecovisionsv.config.ConfigRepository;

import android.util.Log;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.util.concurrent.TimeUnit;

/**
 * Singleton que proporciona la instancia de {@link EcoVisionApi}.
 * La URL sale de BuildConfig.API_BASE_URL (local.properties o variable de entorno
 * API_BASE_URL al compilar); nunca se escribe en el código.
 * Si falta o no es válida, {@link #getApi()} devuelve null y quien llama lo trata
 * como "sin conexión".
 */
public class RetrofitClient {
    private static final String TAG = "RetrofitClient";

    // Timeouts generosos: la inferencia puede tardar ~1-2 s en frío.
    // El de lectura sale de la config (request_timeout_s, defecto 60) en cada petición.
    private static final int CONNECT_TIMEOUT_S = 15;
    private static final int WRITE_TIMEOUT_S   = 30;

    private static volatile EcoVisionApi INSTANCE;

    private RetrofitClient() {}

    /** Devuelve la API, o null si API_BASE_URL falta o no es válida. */
    public static EcoVisionApi getApi() {
        if (INSTANCE == null) {
            synchronized (RetrofitClient.class) {
                if (INSTANCE == null) {
                    INSTANCE = buildApi();
                }
            }
        }
        return INSTANCE;
    }

    private static String getSanitizedBaseUrl() {
        return normalizarUrl(BuildConfig.API_BASE_URL);
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

    private static EcoVisionApi buildApi() {
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        // En release cambia a NONE para no loguear imágenes
        logging.setLevel(HttpLoggingInterceptor.Level.HEADERS);

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
                .writeTimeout(WRITE_TIMEOUT_S,  TimeUnit.SECONDS)
                // Tiempo máximo de espera de la respuesta: request_timeout_s de la config
                .addInterceptor(chain -> chain
                        .withReadTimeout(ConfigRepository.configActual().getRequestTimeoutS(),
                                TimeUnit.SECONDS)
                        .proceed(chain.request()))
                .addInterceptor(logging)
                .build();

        String baseUrl = getSanitizedBaseUrl();
        if (baseUrl == null) {
            Log.e(TAG, "API_BASE_URL no está configurada (local.properties o variable de entorno)");
            return null;
        }

        try {
            return new Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(EcoVisionApi.class);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "API_BASE_URL no es una URL válida", e);
            return null;
        }
    }
}
