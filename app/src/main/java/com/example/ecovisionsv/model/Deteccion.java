package com.example.ecovisionsv.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * Representa una detección individual devuelta por /predict del servidor EcoVision
 * Ejemplo de JSON:
 * {
 *   "categoria": "Metal",
 *   "clase_id": 4,
 *   "confianza": 0.9843,
 *   "bbox_norm": [0.658, 0.1202, 0.8838, 0.7668],
 *   "bbox_px":   [1052,  108,    1414,   690  ]
 * }
 */
public class Deteccion {

    @SerializedName("categoria")
    public String categoria;

    @SerializedName("clase_id")
    public int claseId;

    @SerializedName("confianza")
    public float confianza;

    /** [xmin, ymin, xmax, ymax] normalizado 0–1 respecto a la imagen enviada. */
    @SerializedName("bbox_norm")
    public List<Float> bboxNorm;

    /** [xmin, ymin, xmax, ymax] en píxeles de la imagen ORIGINAL del dispositivo. */
    @SerializedName("bbox_px")
    public List<Integer> bboxPx;

    // helpers

    /** Área de la bbox normalizada; se usa para calcular el IoU. */
    public float areaNorm() {
        if (bboxNorm == null || bboxNorm.size() < 4) return 0f;
        float w = bboxNorm.get(2) - bboxNorm.get(0);
        float h = bboxNorm.get(3) - bboxNorm.get(1);
        return Math.max(0f, w) * Math.max(0f, h);
    }
}
