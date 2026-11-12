package com.example.ecovisionsv.config;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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
 * Todavía no hay petición remota: GET /api/v1/config se conectará cuando el backend
 * esté desplegado. Esa petición llamará a {@link #guardar} con el campo "config" de la
 * respuesta y su ETag (para enviar If-None-Match en la siguiente).
 */
public final class ConfigRepository {

    private static final String PREFS = "app_config";
    private static final String KEY_CONFIG_JSON = "config_json";
    private static final String KEY_ETAG = "etag";
    private static final String KEY_CONFIG_VERSION = "config_version";

    private static volatile ConfigRepository instance;

    private final SharedPreferences prefs;
    private volatile AppConfig actual;

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
