package com.example.ecovisionsv.model;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;

/**
 * Respuesta completa del endpoint POST /predict.
 * Ejemplo:
 * {
 *   "ancho": 640, "alto": 640,
 *   "umbral": 0.4, "latencia_ms": 1200.5,
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

    // filtrado de detecciones solapadas
    /**
     * Devuelve la lista de detecciones filtrada por NMS (Non-Maximum Suppression)
     * por cada grupo de bboxes que se solapan más de [iouThreshold],
     * se conserva únicamente la de mayor confianza.
     *
     * @param iouThreshold  solapamiento mínimo para considerar dos detecciones
     *                      como el mismo objeto
     */
    public List<Deteccion> deteccionesFiltradas(float iouThreshold) {
        if (detecciones == null || detecciones.isEmpty()) return new ArrayList<>();

        // Ordenar por confianza descendente
        List<Deteccion> ordenadas = new ArrayList<>(detecciones);
        ordenadas.sort((a, b) -> Float.compare(b.confianza, a.confianza));

        List<Deteccion> resultado = new ArrayList<>();
        boolean[] suprimida = new boolean[ordenadas.size()];

        for (int i = 0; i < ordenadas.size(); i++) {
            if (suprimida[i]) continue;
            Deteccion actual = ordenadas.get(i);
            resultado.add(actual);

            for (int j = i + 1; j < ordenadas.size(); j++) {
                if (suprimida[j]) continue;
                if (iou(actual, ordenadas.get(j)) >= iouThreshold) {
                    suprimida[j] = true;
                }
            }
        }
        return resultado;
    }

    /** Intersection-over-Union entre dos detecciones usando bbox normalizada. */
    private static float iou(Deteccion a, Deteccion b) {
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
