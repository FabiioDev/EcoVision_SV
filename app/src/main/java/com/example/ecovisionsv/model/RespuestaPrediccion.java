package com.example.ecovisionsv.model;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;

/**
 * Respuesta completa del endpoint POST /predict.
 * Ejemplo:
 * {
 *   "ancho": 640, "alto": 640,
 *   "umbral": 0.3, "latencia_ms": 1200.5,
 *   "detecciones": [ ... ]
 * }
 */
public class RespuestaPrediccion {

    @SerializedName("ancho")
    public int ancho;

    @SerializedName("alto")
    public int alto;

    @SerializedName("umbral")
    public float umbral;

    @SerializedName("latencia_ms")
    public float latenciaMs;

    @SerializedName("detecciones")
    public List<Deteccion> detecciones;

    /**
     * Umbral de IoU por defecto del NMS: dos detecciones con IoU > 0.3 se
     * consideran el mismo objeto. Único lugar donde se define el valor; coincide
     * con el valor por defecto de nms_iou en la config del backend (rango 0–1).
     */
    public static final float NMS_IOU_DEFAULT = 0.3f;

    /**
     * Devuelve las detecciones ordenadas por confianza descendente, sin filtrar.
     */
    public List<Deteccion> deteccionesOrdenadas() {
        if (detecciones == null || detecciones.isEmpty()) return new ArrayList<>();
        List<Deteccion> ordenadas = new ArrayList<>(detecciones);
        ordenadas.sort((a, b) -> Float.compare(b.confianza, a.confianza));
        return ordenadas;
    }

    /**
     * Devuelve las detecciones filtradas por NMS con el umbral por defecto
     * ({@link #NMS_IOU_DEFAULT}), ordenadas por confianza descendente.
     */
    public List<Deteccion> deteccionesFiltradas() {
        return deteccionesFiltradas(NMS_IOU_DEFAULT);
    }

    /**
     * Devuelve la lista de detecciones filtrada por NMS (Non-Maximum Suppression):
     * por cada grupo de bboxes que se solapan más de [iouThreshold],
     * se conserva únicamente la de mayor confianza.
     *
     * @param iouThreshold  solapamiento mínimo para considerar dos detecciones
     *                      como el mismo objeto
     */
    public List<Deteccion> deteccionesFiltradas(float iouThreshold) {
        List<Deteccion> ordenadas = deteccionesOrdenadas();
        if (ordenadas.isEmpty()) return ordenadas;

        List<Deteccion> resultado = new ArrayList<>();
        boolean[] suprimida = new boolean[ordenadas.size()];

        for (int i = 0; i < ordenadas.size(); i++) {
            if (suprimida[i]) continue;
            Deteccion actual = ordenadas.get(i);
            resultado.add(actual);

            for (int j = i + 1; j < ordenadas.size(); j++) {
                if (suprimida[j]) continue;
                // Misma regla que la demo web y el contrato: se suprime si IoU > umbral.
                // 0 = elimina cualquier solapamiento; 1 = no elimina ninguna.
                if (iou(actual, ordenadas.get(j)) > iouThreshold) {
                    suprimida[j] = true;
                }
            }
        }
        return resultado;
    }

    /** Intersection-over-Union entre dos detecciones usando bbox normalizada. */
    static float iou(Deteccion a, Deteccion b) {
        if (a.bboxNorm == null || b.bboxNorm == null
                || a.bboxNorm.size() < 4 || b.bboxNorm.size() < 4) return 0f;

        float xminA = a.bboxNorm.get(0), yminA = a.bboxNorm.get(1);
        float xmaxA = a.bboxNorm.get(2), ymaxA = a.bboxNorm.get(3);

        float xminB = b.bboxNorm.get(0), yminB = b.bboxNorm.get(1);
        float xmaxB = b.bboxNorm.get(2), ymaxB = b.bboxNorm.get(3);

        float interX = Math.max(0f, Math.min(xmaxA, xmaxB) - Math.max(xminA, xminB));
        float interY = Math.max(0f, Math.min(ymaxA, ymaxB) - Math.max(yminA, yminB));
        float inter  = interX * interY;
        if (inter == 0f) return 0f;

        float union = a.areaNorm() + b.areaNorm() - inter;
        return union <= 0f ? 0f : inter / union;
    }
}
