package com.example.ecovisionsv.network;

import com.example.ecovisionsv.BuildConfig;
import com.example.ecovisionsv.api.EcoVisionApi;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.util.concurrent.TimeUnit;

/**
 * Singleton que proporciona la instancia de {@link EcoVisionApi}.
 * IMPORTANTE: reemplaza BASE_URL con la URL real de tu servicio Cloud Run.
 * Formato: "https://<nombre-servicio>-<hash>-<region>.a.run.app/"
 * El slash final es obligatorio en Retrofit.
 */
public class RetrofitClient {
    // Cambiar URL por la del contenedor en Cloud Run
    private static final String BASE_URL = BuildConfig.API_BASE_URL;

    // Timeouts generosos: la inferencia puede tardar ~1-2 s en frío
    private static final int CONNECT_TIMEOUT_S = 15;
    private static final int READ_TIMEOUT_S    = 60;
    private static final int WRITE_TIMEOUT_S   = 30;

    private static volatile EcoVisionApi INSTANCE;

    private RetrofitClient() {}

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

    private static EcoVisionApi buildApi() {
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        // En release cambia a NONE para no loguear imágenes
        logging.setLevel(HttpLoggingInterceptor.Level.HEADERS);

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT_S,    TimeUnit.SECONDS)
                .writeTimeout(WRITE_TIMEOUT_S,  TimeUnit.SECONDS)
                .addInterceptor(logging)
                .build();

        return new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(EcoVisionApi.class);
    }
}
