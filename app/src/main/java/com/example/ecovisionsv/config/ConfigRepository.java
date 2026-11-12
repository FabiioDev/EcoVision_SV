package com.example.ecovisionsv.config;

import android.content.Context;
import android.content.SharedPreferences;

import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.ecovisionsv.api.BackendApi;
import com.example.ecovisionsv.network.BackendClient;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.ResponseBody;
import retrofit2.Response;

/**
 * Único punto de acceso a la config de la app.
 *
 * Las pantallas y repositorios leen la config SOLO con {@link #get()}; nunca leen
 * SharedPreferences ni el JSON remoto directamente.
 *
 * Orden de prioridad:
 *  1. la última config válida guardada en este dispositivo;
 *  2. si no hay ninguna, {@link AppConfig#DEFAULTS} (comportamiento actual de la app).
 *
 * {@link #refrescar()} pide GET /api/v1/config al backend (BACKEND_BASE_URL) con
 * If-None-Match y guarda la respuesta si es nueva. La config vigente cambia en cuanto llega;
 * las pantallas la leen de nuevo al mostrarse.
 */
public final class ConfigRepository {

    private static final String PREFS = "app_config";
    private static final String KEY_CONFIG_JSON = "config_json";
    private static final String KEY_ETAG = "etag";
    private static final String KEY_CONFIG_VERSION = "config_version";

    private static final String TAG = "ConfigRepository";

    /** Tiempo mínimo entre dos peticiones de config (al cambiar de pantalla, etc.). */
    static final long INTERVALO_MIN_MS = 30_000L;

    /** Hilo propio: la petición nunca ocupa el hilo principal ni el pool de la BD. */
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private static volatile ConfigRepository instance;

    private final SharedPreferences prefs;
    private volatile AppConfig actual;
    private boolean refrescando = false;
    private long ultimoIntento = 0L;

    private ConfigRepository(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        // Lectura local y rápida: nunca bloquea el arranque con red
        actual = AppConfig.fromJson(prefs.getString(KEY_CONFIG_JSON, null));
    }

    @NonNull
    public static ConfigRepository getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (ConfigRepository.class) {
                if (instance == null) {
                    instance = new ConfigRepository(context);
                }
            }
        }
        return instance;
    }

    /** Config vigente. Nunca es null. */
    @NonNull
    public AppConfig get() {
        return actual;
    }

    /** ETag de la última config guardada, para If-None-Match; null si no hay. */
    @Nullable
    public String getEtag() {
        return prefs.getString(KEY_ETAG, null);
    }

    /**
     * Número del último cambio publicado desde el panel (0 = valores por defecto, -1 = sin
     * config guardada). Solo para depuración y caché: NO es la versión de la app y no se
     * muestra como tal.
     */
    public int getConfigVersion() {
        return prefs.getInt(KEY_CONFIG_VERSION, -1);
    }

    /**
     * Pide la config al backend en segundo plano, como mucho una vez cada
     * {@link #INTERVALO_MIN_MS}. Nunca bloquea ni lanza excepciones:
     *  - 200 → guarda la config nueva y su ETag;
     *  - 304 → la guardada sigue vigente;
     *  - error, timeout, sin red o sin BACKEND_BASE_URL → no cambia nada.
     * Se llama al abrir la app y al volver a primer plano (MainActivity.onResume).
     */
    public void refrescar() {
        long ahora = SystemClock.elapsedRealtime();
        synchronized (this) {
            if (refrescando || (ultimoIntento != 0 && ahora - ultimoIntento < INTERVALO_MIN_MS)) {
                return;
            }
            refrescando = true;
            ultimoIntento = ahora;
        }
        EXECUTOR.execute(() -> {
            try {
                BackendApi api = BackendClient.getApi();
                if (api == null) return;
                Response<ResponseBody> resp = api.config(getEtag()).execute();
                String cuerpo = null;
                try (ResponseBody body = resp.body()) {
                    if (body != null) cuerpo = body.string();
                }
                ConfigResponse r = ConfigResponse.interpretar(resp.code(), cuerpo);
                if (r.tipo == ConfigResponse.Tipo.NUEVA && r.configJson != null) {
                    guardar(r.configJson, resp.headers().get("ETag"), r.configVersion);
                } else if (r.tipo == ConfigResponse.Tipo.INVALIDA) {
                    Log.w(TAG, "Config remota no válida (HTTP " + resp.code() + "): se conserva la guardada");
                }
            } catch (Exception e) {
                Log.w(TAG, "No se pudo obtener la config remota: se conserva la guardada", e);
            } finally {
                synchronized (this) {
                    refrescando = false;
                }
            }
        });
    }

    /**
     * Guarda una config recibida del backend y la convierte en la vigente.
     *
     * @param configJson    objeto "config" de la respuesta de GET /api/v1/config
     * @param etag          cabecera ETag de la respuesta (puede ser null)
     * @param configVersion campo "config_version" de la respuesta
     */
    public synchronized void guardar(@NonNull String configJson, @Nullable String etag,
                                     int configVersion) {
        actual = AppConfig.fromJson(configJson);
        prefs.edit()
                .putString(KEY_CONFIG_JSON, configJson)
                .putString(KEY_ETAG, etag)
                .putInt(KEY_CONFIG_VERSION, configVersion)
                .apply();
    }
}
