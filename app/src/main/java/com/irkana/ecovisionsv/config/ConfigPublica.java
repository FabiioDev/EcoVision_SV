package com.irkana.ecovisionsv.config;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

/**
 * Respuesta de GET /api/v1/config/public (sin autenticación): lo que la app necesita saber
 * ANTES del login. Contrato: #/components/schemas/PublicConfig.
 *
 * Tolerante igual que {@link AppConfig}: claves desconocidas se ignoran y las ausentes o
 * de tipo incorrecto quedan en null (la pantalla usa entonces la config guardada).
 *
 * {@code registration_enabled} todavía no está en el contrato (propuesto en
 * ecovision_admin/TODO.txt, punto 5): mientras el backend no lo envíe, es null.
 */
public final class ConfigPublica {

    @Nullable public final Boolean maintenanceMode;
    @Nullable public final String maintenanceMessage;
    @Nullable public final Boolean registrationEnabled;

    private ConfigPublica(@Nullable Boolean maintenanceMode, @Nullable String maintenanceMessage,
                          @Nullable Boolean registrationEnabled) {
        this.maintenanceMode = maintenanceMode;
        this.maintenanceMessage = maintenanceMessage;
        this.registrationEnabled = registrationEnabled;
    }

    /** null si el cuerpo no es un objeto JSON válido. */
    @Nullable
    public static ConfigPublica fromJson(@Nullable String json) {
        if (json == null || json.trim().isEmpty()) return null;
        try {
            JsonElement el = JsonParser.parseString(json);
            if (!el.isJsonObject()) return null;
            JsonObject o = el.getAsJsonObject();
            String mensaje = str(o, "maintenance_message");
            if (mensaje != null) {
                mensaje = mensaje.trim();
                if (mensaje.length() > AppConfig.MAINTENANCE_MESSAGE_MAX_LEN) {
                    mensaje = mensaje.substring(0, AppConfig.MAINTENANCE_MESSAGE_MAX_LEN);
                }
                if (mensaje.isEmpty()) mensaje = null;
            }
            return new ConfigPublica(bool(o, "maintenance_mode"), mensaje,
                    bool(o, "registration_enabled"));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** maintenance_mode de esta respuesta, o el de {@code guardada} si no vino. */
    public boolean enMantenimiento(@NonNull AppConfig guardada) {
        return maintenanceMode != null ? maintenanceMode : guardada.isMaintenanceMode();
    }

    @NonNull
    public String mensajeMantenimiento(@NonNull AppConfig guardada) {
        return maintenanceMessage != null ? maintenanceMessage : guardada.getMaintenanceMessage();
    }

    public boolean registroPermitido(@NonNull AppConfig guardada) {
        return registrationEnabled != null ? registrationEnabled : guardada.isRegistrationEnabled();
    }

    @Nullable
    private static Boolean bool(JsonObject o, String key) {
        JsonElement el = o.get(key);
        if (el == null || !el.isJsonPrimitive()) return null;
        JsonPrimitive p = el.getAsJsonPrimitive();
        return p.isBoolean() ? p.getAsBoolean() : null;
    }

    @Nullable
    private static String str(JsonObject o, String key) {
        JsonElement el = o.get(key);
        if (el == null || !el.isJsonPrimitive()) return null;
        JsonPrimitive p = el.getAsJsonPrimitive();
        return p.isString() ? p.getAsString() : null;
    }
}
