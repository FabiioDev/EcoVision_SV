package com.irkana.ecovisionsv.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ConfigPublicaTest {

    private static final AppConfig GUARDADA = AppConfig.DEFAULTS;

    @Test
    public void respuestaDelContrato_sinRegistro_usaLaGuardada() {
        ConfigPublica c = ConfigPublica.fromJson("{\"config_version\": 7, \"maintenance_mode\": true,"
                + " \"maintenance_message\": \" Volvemos pronto \", \"min_app_version\": \"1.0\"}");
        assertNotNull(c);
        assertTrue(c.enMantenimiento(GUARDADA));
        assertEquals("Volvemos pronto", c.mensajeMantenimiento(GUARDADA));
        assertNull(c.registrationEnabled);
        assertEquals(GUARDADA.isRegistrationEnabled(), c.registroPermitido(GUARDADA));
    }

    @Test
    public void registrationEnabled_cuandoElBackendLoEnvie() {
        ConfigPublica c = ConfigPublica.fromJson("{\"registration_enabled\": false}");
        assertNotNull(c);
        assertFalse(c.registroPermitido(GUARDADA));
    }

    @Test
    public void tiposIncorrectosYMensajeVacio_usanLaGuardada() {
        ConfigPublica c = ConfigPublica.fromJson("{\"maintenance_mode\": \"si\","
                + " \"maintenance_message\": \"  \", \"registration_enabled\": 1}");
        assertNotNull(c);
        assertEquals(GUARDADA.isMaintenanceMode(), c.enMantenimiento(GUARDADA));
        assertEquals(GUARDADA.getMaintenanceMessage(), c.mensajeMantenimiento(GUARDADA));
        assertEquals(GUARDADA.isRegistrationEnabled(), c.registroPermitido(GUARDADA));
    }

    @Test
    public void cuerpoNoValido_esNull() {
        assertNull(ConfigPublica.fromJson(null));
        assertNull(ConfigPublica.fromJson(""));
        assertNull(ConfigPublica.fromJson("[1,2]"));
        assertNull(ConfigPublica.fromJson("{no es json"));
    }

    @Test
    public void mensajeLargo_seRecorta() {
        String largo = "a".repeat(AppConfig.MAINTENANCE_MESSAGE_MAX_LEN + 50);
        ConfigPublica c = ConfigPublica.fromJson("{\"maintenance_message\": \"" + largo + "\"}");
        assertNotNull(c);
        assertEquals(AppConfig.MAINTENANCE_MESSAGE_MAX_LEN, c.maintenanceMessage.length());
    }
}
