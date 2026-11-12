package com.example.ecovisionsv.config;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Config v1 de la app (flags y parámetros), inmutable.
 *
 * Es la ÚNICA definición en la app de las claves, sus valores por defecto y sus límites.
 * Debe coincidir con el contrato: ecovision_admin/docs/openapi.yaml
 * (#/components/schemas/AppConfig). Si discrepan, manda el contrato.
 *
 * Con {@link #DEFAULTS} la app se comporta exactamente como antes de existir la config.
 *
 * Las dependencias entre flags se resuelven AQUÍ y en ningún otro sitio:
 *  - history_enabled=false       ⇒ history_save_enabled=false y export_enabled=false
 *  - save_to_gallery_enabled=false ⇒ history_save_enabled=false (la foto no se guarda en
 *    ningún sitio, así que la detección tampoco entra en el historial)
 *  - announcements_enabled=false ⇒ no se muestra ningún anuncio
 * Por eso los getters devuelven el valor ya resuelto; las pantallas no deben combinar flags.
 *
 * Lectura tolerante ({@link #fromJson(String)}): se ignoran las claves desconocidas; las
 * ausentes, con tipo incorrecto o no válidas toman su valor por defecto; los números fuera
 * de rango se recortan al límite (igual que AppConfig.from_stored en el backend).
 */
public final class AppConfig {

    // --- Límites (iguales a los del contrato) ---
    public static final int ANNOUNCEMENT_SLOTS = 3;
    public static final int ANNOUNCEMENT_MAX_LEN = 500;
    public static final int MAINTENANCE_MESSAGE_MAX_LEN = 500;
    public static final float SCORE_THRESHOLD_MIN = 0.3f;
    public static final float SCORE_THRESHOLD_MAX = 1f;
    public static final float NMS_IOU_MIN = 0f;
    public static final float NMS_IOU_MAX = 1f;
    public static final int MAX_DETECTIONS_MIN = 3;
    public static final int MAX_DETECTIONS_MAX = 100;
    public static final int REQUEST_TIMEOUT_S_MIN = 5;
    public static final int REQUEST_TIMEOUT_S_MAX = 120;
    public static final int RETRIES_MIN = 0;
    public static final int RETRIES_MAX = 5;

    private static final Pattern VERSION_PATTERN = Pattern.compile("^\\d+(\\.\\d+){0,3}$");

    // --- Valores por defecto (= comportamiento actual de la app) ---
    static final String DEFAULT_MAINTENANCE_MESSAGE =
            "Estamos realizando tareas de mantenimiento. Vuelve a intentarlo más tarde.";
    static final String DEFAULT_MIN_APP_VERSION = "1.0";
    static final float DEFAULT_SCORE_THRESHOLD = 0.3f;
    static final float DEFAULT_NMS_IOU = 0.3f;
    static final int DEFAULT_MAX_DETECTIONS = 100;
    static final int DEFAULT_REQUEST_TIMEOUT_S = 60;
    static final int DEFAULT_RETRIES = 0;

    /** Config por defecto del código: la que se usa sin caché ni backend. */
    public static final AppConfig DEFAULTS = new AppConfig(new JsonObject());

    // --- Funciones de la app ---
    private final boolean cameraEnabled;
    private final boolean galleryEnabled;
    private final boolean historyEnabled;
    private final boolean historySaveEnabled;
    private final boolean exportEnabled;
    private final boolean saveToGalleryEnabled;
    private final boolean aboutEnabled;
    private final boolean tipsEnabled;
    private final boolean showApiStatus;
    /** Sin efecto hasta la Fase 5. */
    private final boolean cloudSyncEnabled;

    // --- Anuncios ---
    private final boolean announcementsEnabled;
    private final List<String> announcements;

    // --- Cuentas (efecto desde la Fase 3) ---
    private final boolean registrationEnabled;

    // --- Mantenimiento y versión ---
    private final boolean maintenanceMode;
    private final String maintenanceMessage;
    private final String minAppVersion;

    // --- Detección ---
    private final float scoreThreshold;
    private final boolean nmsEnabled;
    private final float nmsIou;
    private final int maxDetections;

    // --- Red ---
    private final int requestTimeoutS;
    private final int retries;

    // demo_enabled no se lee: es la demo web y la app la ignora.

    private AppConfig(JsonObject o) {
        cameraEnabled = bool(o, "camera_enabled", true);
        galleryEnabled = bool(o, "gallery_enabled", true);
        historyEnabled = bool(o, "history_enabled", true);
        historySaveEnabled = bool(o, "history_save_enabled", true);
        exportEnabled = bool(o, "export_enabled", true);
        saveToGalleryEnabled = bool(o, "save_to_gallery_enabled", true);
        aboutEnabled = bool(o, "about_enabled", true);
        tipsEnabled = bool(o, "tips_enabled", true);
        showApiStatus = bool(o, "show_api_status", true);
        cloudSyncEnabled = bool(o, "cloud_sync_enabled", false);

        announcementsEnabled = bool(o, "announcements_enabled", true);
        List<String> anuncios = new ArrayList<>();
        for (int i = 1; i <= ANNOUNCEMENT_SLOTS; i++) {
            String texto = str(o, "announcement_" + i, "", ANNOUNCEMENT_MAX_LEN);
            if (!texto.isEmpty()) anuncios.add(texto);
        }
        announcements = Collections.unmodifiableList(anuncios);

        registrationEnabled = bool(o, "registration_enabled", true);

        maintenanceMode = bool(o, "maintenance_mode", false);
        String mensaje = str(o, "maintenance_message", DEFAULT_MAINTENANCE_MESSAGE,
                MAINTENANCE_MESSAGE_MAX_LEN);
        maintenanceMessage = mensaje.isEmpty() ? DEFAULT_MAINTENANCE_MESSAGE : mensaje;
        String version = str(o, "min_app_version", DEFAULT_MIN_APP_VERSION, Integer.MAX_VALUE);
        minAppVersion = VERSION_PATTERN.matcher(version).matches() ? version : DEFAULT_MIN_APP_VERSION;

        scoreThreshold = num(o, "score_threshold", DEFAULT_SCORE_THRESHOLD,
                SCORE_THRESHOLD_MIN, SCORE_THRESHOLD_MAX);
        nmsEnabled = bool(o, "nms_enabled", true);
        nmsIou = num(o, "nms_iou", DEFAULT_NMS_IOU, NMS_IOU_MIN, NMS_IOU_MAX);
        maxDetections = entero(o, "max_detections", DEFAULT_MAX_DETECTIONS,
                MAX_DETECTIONS_MIN, MAX_DETECTIONS_MAX);

        requestTimeoutS = entero(o, "request_timeout_s", DEFAULT_REQUEST_TIMEOUT_S,
                REQUEST_TIMEOUT_S_MIN, REQUEST_TIMEOUT_S_MAX);
        retries = entero(o, "retries", DEFAULT_RETRIES, RETRIES_MIN, RETRIES_MAX);
    }

    // ------------------------------------------------------------------ //
    // Lectura
    // ------------------------------------------------------------------ //

    /**
     * Construye la config a partir del objeto AppConfig en JSON (el campo "config" de
     * GET /api/v1/config). Nunca lanza excepciones: si el JSON es null, está vacío, no es
     * un objeto o es inválido, devuelve {@link #DEFAULTS}.
     */
    @NonNull
    public static AppConfig fromJson(@Nullable String json) {
        if (json == null || json.trim().isEmpty()) return DEFAULTS;
        try {
            JsonElement el = JsonParser.parseString(json);
            return el.isJsonObject() ? new AppConfig(el.getAsJsonObject()) : DEFAULTS;
        } catch (RuntimeException e) {
            return DEFAULTS;
        }
    }

    @Nullable
    private static JsonPrimitive primitiva(JsonObject o, String key) {
        JsonElement el = o.get(key);
        return el != null && el.isJsonPrimitive() ? el.getAsJsonPrimitive() : null;
    }

    private static boolean bool(JsonObject o, String key, boolean def) {
        JsonPrimitive p = primitiva(o, key);
        return p != null && p.isBoolean() ? p.getAsBoolean() : def;
    }

    private static String str(JsonObject o, String key, String def, int maxLen) {
        JsonPrimitive p = primitiva(o, key);
        if (p == null || !p.isString()) return def;
        String v = p.getAsString().trim();
        return v.length() > maxLen ? v.substring(0, maxLen) : v;
    }

    private static float num(JsonObject o, String key, float def, float min, float max) {
        JsonPrimitive p = primitiva(o, key);
        if (p == null || !p.isNumber()) return def;
        double v = p.getAsDouble();
        if (Double.isNaN(v)) return def;
        return (float) Math.max(min, Math.min(max, v));
    }

    private static int entero(JsonObject o, String key, int def, int min, int max) {
        JsonPrimitive p = primitiva(o, key);
        if (p == null || !p.isNumber()) return def;
        double v = p.getAsDouble();
        // El contrato exige enteros: un decimal se considera no válido
        if (Double.isNaN(v) || v != Math.rint(v)) return def;
        return (int) Math.max(min, Math.min(max, v));
    }

    // ------------------------------------------------------------------ //
    // Funciones de la app (dependencias ya resueltas)
    // ------------------------------------------------------------------ //

    public boolean isCameraEnabled() { return cameraEnabled; }

    public boolean isGalleryEnabled() { return galleryEnabled; }

    public boolean isHistoryEnabled() { return historyEnabled; }

    /**
     * false si el historial está desactivado o si no se guardan las fotos
     * (save_to_gallery_enabled=false: sin foto no hay entrada en el historial).
     */
    public boolean isHistorySaveEnabled() {
        return historyEnabled && historySaveEnabled && saveToGalleryEnabled;
    }

    /** false si el historial está desactivado. */
    public boolean isExportEnabled() { return historyEnabled && exportEnabled; }

    /** false: la foto solo se usa temporalmente para mostrar el resultado. */
    public boolean isSaveToGalleryEnabled() { return saveToGalleryEnabled; }

    public boolean isAboutEnabled() { return aboutEnabled; }

    public boolean isTipsEnabled() { return tipsEnabled; }

    public boolean isShowApiStatus() { return showApiStatus; }

    /** Sin efecto hasta la Fase 5 (no hay sincronización implementada). */
    public boolean isCloudSyncEnabled() { return cloudSyncEnabled; }

    // ------------------------------------------------------------------ //
    // Anuncios
    // ------------------------------------------------------------------ //

    /**
     * Anuncios a mostrar, en orden (announcement_1..3), sin los bloques vacíos.
     * Lista vacía si announcements_enabled=false o si no hay ningún texto: en ese caso
     * la sección de anuncios no se muestra.
     */
    @NonNull
    public List<String> getAnnouncements() {
        return announcementsEnabled ? announcements : Collections.emptyList();
    }

    // ------------------------------------------------------------------ //
    // Cuentas, mantenimiento y versión
    // ------------------------------------------------------------------ //

    /** Efecto desde la Fase 3. */
    public boolean isRegistrationEnabled() { return registrationEnabled; }

    public boolean isMaintenanceMode() { return maintenanceMode; }

    @NonNull
    public String getMaintenanceMessage() { return maintenanceMessage; }

    /** versionName mínima admitida (p. ej. "1.0" o "1.2.3"). */
    @NonNull
    public String getMinAppVersion() { return minAppVersion; }

    // ------------------------------------------------------------------ //
    // Detección
    // ------------------------------------------------------------------ //

    /** Umbral de confianza (0.3–1); se envía a /predict como ?umbral=. */
    public float getScoreThreshold() { return scoreThreshold; }

    public boolean isNmsEnabled() { return nmsEnabled; }

    /** Se suprime una caja si su IoU con otra de más confianza es MAYOR que este valor (0–1). */
    public float getNmsIou() { return nmsIou; }

    /** Máximo de detecciones mostradas (3–100), aplicado después del NMS. */
    public int getMaxDetections() { return maxDetections; }

    // ------------------------------------------------------------------ //
    // Red
    // ------------------------------------------------------------------ //

    /** Tiempo máximo de espera de /predict, en segundos (5–120). */
    public int getRequestTimeoutS() { return requestTimeoutS; }

    /** Reintentos de /predict ante fallo de red (0–5). */
    public int getRetries() { return retries; }
}
