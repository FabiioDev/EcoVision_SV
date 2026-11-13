package com.irkana.ecovisionsv.model;

import com.google.gson.annotations.SerializedName;

/**
 * Respuesta de GET /api/v1/health (backend):
 * {"status":"ok","service":"backend","version":"0.1.0","inferencia":"lista"}
 *
 * {@code status} describe solo el backend; {@code inferencia} el modelo:
 * lista, despertando (arranque en frío), no_disponible o sin_configurar.
 */
public class RespuestaHealth {

    public static final String LISTA = "lista";
    public static final String DESPERTANDO = "despertando";

    /** Cómo interpreta la app la respuesta para el indicador de conexión. */
    public enum Lectura {
        /** Backend y modelo listos: se puede detectar. */
        LISTO,
        /** El modelo está arrancando: volver a consultar pronto. */
        DESPERTANDO,
        /** Backend con error, modelo no disponible o sin configurar. */
        FALLO
    }

    @SerializedName("status")
    public String status;

    @SerializedName("inferencia")
    public String inferencia;

    public boolean isOk() {
        return "ok".equalsIgnoreCase(status);
    }

    public Lectura leer() {
        if (!isOk()) return Lectura.FALLO;
        if (LISTA.equals(inferencia)) return Lectura.LISTO;
        if (DESPERTANDO.equals(inferencia)) return Lectura.DESPERTANDO;
        return Lectura.FALLO;
    }
}
