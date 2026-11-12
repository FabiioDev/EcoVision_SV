package com.irkana.ecovisionsv.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class ConfigResponseTest {

    @Test
    public void respuesta200_devuelveConfigYVersion() {
        ConfigResponse r = ConfigResponse.interpretar(200,
                "{\"config_version\":12,\"updated_at\":\"2026-11-12T10:00:00Z\","
                        + "\"config\":{\"camera_enabled\":false,\"max_detections\":10}}");
        assertEquals(ConfigResponse.Tipo.NUEVA, r.tipo);
        assertEquals(12, r.configVersion);
        AppConfig c = AppConfig.fromJson(r.configJson);
        assertFalse(c.isCameraEnabled());
        assertEquals(10, c.getMaxDetections());
    }

    @Test
    public void respuesta200_sinVersionNiFecha_usaVersion0() {
        ConfigResponse r = ConfigResponse.interpretar(200, "{\"config\":{}}");
        assertEquals(ConfigResponse.Tipo.NUEVA, r.tipo);
        assertEquals(0, r.configVersion);
    }

    @Test
    public void respuesta304_sinCambios() {
        ConfigResponse r = ConfigResponse.interpretar(304, null);
        assertEquals(ConfigResponse.Tipo.SIN_CAMBIOS, r.tipo);
        assertNull(r.configJson);
    }

    @Test
    public void erroresHttp_sonInvalidos() {
        assertEquals(ConfigResponse.Tipo.INVALIDA, ConfigResponse.interpretar(401, "{}").tipo);
        assertEquals(ConfigResponse.Tipo.INVALIDA, ConfigResponse.interpretar(503, "{\"detail\":\"x\"}").tipo);
        assertEquals(ConfigResponse.Tipo.INVALIDA, ConfigResponse.interpretar(500, null).tipo);
    }

    @Test
    public void cuerposNoValidos_sonInvalidos() {
        String[] cuerpos = {
                null, "", "no es json", "[]", "{}", "{\"config\":null}",
                "{\"config\":[1,2]}", "{\"config\":\"texto\"}",
        };
        for (String cuerpo : cuerpos) {
            assertEquals("cuerpo: " + cuerpo, ConfigResponse.Tipo.INVALIDA,
                    ConfigResponse.interpretar(200, cuerpo).tipo);
        }
    }
}
