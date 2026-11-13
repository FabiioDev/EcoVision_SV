package com.irkana.ecovisionsv.network;

import android.util.Log;

import com.irkana.ecovisionsv.BuildConfig;
import com.irkana.ecovisionsv.api.BackendApi;
import com.irkana.ecovisionsv.auth.AuthRepository;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;

/**
 * Singleton que proporciona la instancia de {@link BackendApi}.
 * La URL sale de BuildConfig.BACKEND_BASE_URL (local.properties o variable de entorno
 * BACKEND_BASE_URL al compilar); nunca se escribe en el código.
 * Si falta o no es válida, {@link #getApi()} devuelve null y la app usa la config guardada
 * o la de por defecto.
 *
 * Misma URL que {@link RetrofitClient} (detección), pero cliente separado: timeouts cortos (la config nunca debe hacer esperar
 * al usuario) y el token de Firebase (AuthInterceptor).
 */
public final class BackendClient {
    private static final String TAG = "BackendClient";

    /** Timeout corto (2–3 s según el contrato): si el backend tarda, se usa la caché. */
    private static final int TIMEOUT_S = 3;

    private static volatile BackendApi INSTANCE;
    private static volatile boolean construido = false;

    private BackendClient() {}

    /** Devuelve la API, o null si BACKEND_BASE_URL falta o no es válida. */
    public static BackendApi getApi() {
        if (!construido) {
            synchronized (BackendClient.class) {
                if (!construido) {
                    INSTANCE = buildApi();
                    construido = true;
                }
            }
        }
        return INSTANCE;
    }

    private static BackendApi buildApi() {
        String baseUrl = RetrofitClient.normalizarUrl(BuildConfig.BACKEND_BASE_URL);
        if (baseUrl == null) {
            Log.w(TAG, "BACKEND_BASE_URL no está configurada: se usa la config guardada o la de por defecto");
            return null;
        }

        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(BuildConfig.DEBUG
                ? HttpLoggingInterceptor.Level.HEADERS
                : HttpLoggingInterceptor.Level.NONE);
        // El token nunca aparece en el log (se muestra como ██)
        logging.redactHeader("Authorization");

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(TIMEOUT_S, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_S, TimeUnit.SECONDS)
                .writeTimeout(TIMEOUT_S, TimeUnit.SECONDS)
                .callTimeout(TIMEOUT_S, TimeUnit.SECONDS)
                // Token de Firebase (Fase 3). Va antes del log, que muestra la cabecera
                // ya añadida pero con el valor oculto.
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
                    .build()
                    .create(BackendApi.class);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "BACKEND_BASE_URL no es una URL válida", e);
            return null;
        }
    }
}
