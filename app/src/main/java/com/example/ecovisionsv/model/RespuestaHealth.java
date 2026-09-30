package com.example.ecovisionsv.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * Respuesta del endpoint GET /health.
 * {"status":"ok","clases":["Plastico","Metal","Papel","Carton","Vidrio","Organico"]}
 */
public class RespuestaHealth {

    @SerializedName("status")
    public String status;

    @SerializedName("clases")
    public List<String> clases;

    public boolean isOk() {
        return "ok".equalsIgnoreCase(status);
    }
}
