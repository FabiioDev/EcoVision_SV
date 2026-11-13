package com.irkana.ecovisionsv.model;

import static org.junit.Assert.assertEquals;

import com.google.gson.Gson;
import com.irkana.ecovisionsv.model.RespuestaHealth.Lectura;

import org.junit.Test;

public class RespuestaHealthTest {

    private static Lectura leer(String json) {
        return new Gson().fromJson(json, RespuestaHealth.class).leer();
    }

    @Test
    public void lista_esConectado() {
        assertEquals(Lectura.LISTO, leer("{\"status\":\"ok\",\"service\":\"backend\",\"inferencia\":\"lista\"}"));
    }

    @Test
    public void despertando_noEsFallo() {
        assertEquals(Lectura.DESPERTANDO, leer("{\"status\":\"ok\",\"inferencia\":\"despertando\"}"));
    }

    @Test
    public void modeloNoDisponibleOSinConfigurar_esFallo() {
        assertEquals(Lectura.FALLO, leer("{\"status\":\"ok\",\"inferencia\":\"no_disponible\"}"));
        assertEquals(Lectura.FALLO, leer("{\"status\":\"ok\",\"inferencia\":\"sin_configurar\"}"));
    }

    @Test
    public void sinCampoInferenciaOBackendMal_esFallo() {
        // Un /health sin "inferencia" no garantiza que se pueda detectar
        assertEquals(Lectura.FALLO, leer("{\"status\":\"ok\"}"));
        assertEquals(Lectura.FALLO, leer("{\"status\":\"error\",\"inferencia\":\"lista\"}"));
    }
}
