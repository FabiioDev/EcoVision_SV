package com.example.ecovisionsv.config;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

/**
 * Interpreta la respuesta HTTP de GET /api/v1/config (sin dependencias de Android, para
 * poder probarla con JUnit).
 *
 * Cuerpo esperado (contrato, #/components/schemas/ConfigResponse):
 * {"config_version": 12, "updated_at": "...", "config": { ...AppConfig... }}
 */
public final class ConfigResponse {

    public enum Tipo {
        /** 200 con un objeto "config": hay que guardarla. */
        NUEVA,
        /** 304: la config guardada sigue vigente. */
        SIN_CAMBIOS,
        /** Error HTTP o cuerpo no válido: se conserva la config guardada. */
        INVALIDA
    }

    public final Tipo tipo;
    /** Objeto "config" en JSON; solo con {@link Tipo#NUEVA}. */
    @Nullable public final String configJson;
    /** Campo "config_version" (0 si falta); solo con {@link Tipo#NUEVA}. NO es la versión de la app. */
    public final int configVersion;

    private ConfigResponse(Tipo tipo, @Nullable String configJson, int configVersion) {
        this.tipo = tipo;
        this.configJson = configJson;
        this.configVersion = configVersion;
    }

    private static final ConfigResponse SIN_CAMBIOS = new ConfigResponse(Tipo.SIN_CAMBIOS, null, 0);
    private static final ConfigResponse INVALIDA = new ConfigResponse(Tipo.INVALIDA, null, 0);

    /**
     * @param codigoHttp código de estado de la respuesta
     * @param cuerpo     cuerpo de la respuesta (null si no hay)
     */
    @NonNull
    public static ConfigResponse interpretar(int codigoHttp, @Nullable String cuerpo) {
        if (codigoHttp == 304) return SIN_CAMBIOS;
        if (codigoHttp != 200 || cuerpo == null) return INVALIDA;
        try {
            JsonElement raiz = JsonParser.parseString(cuerpo);
            if (!raiz.isJsonObject()) return INVALIDA;
            JsonObject obj = raiz.getAsJsonObject();
            JsonElement config = obj.get("config");
            if (config == null || !config.isJsonObject()) return INVALIDA;
            int version = 0;
            JsonElement v = obj.get("config_version");
            if (v != null && v.isJsonPrimitive()) {
                JsonPrimitive p = v.getAsJsonPrimitive();
                if (p.isNumber()) version = p.getAsInt();
            }
            return new ConfigResponse(Tipo.NUEVA, config.toString(), version);
        } catch (RuntimeException e) {
            return INVALIDA;
        }
    }
}
