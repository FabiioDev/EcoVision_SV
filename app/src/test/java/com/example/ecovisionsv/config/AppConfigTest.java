package com.example.ecovisionsv.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.example.ecovisionsv.model.RespuestaPrediccion;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class AppConfigTest {

    private static final float DELTA = 1e-6f;

    // --- Valores por defecto = contrato = comportamiento actual de la app ---

    @Test
    public void valoresPorDefecto_coincidenConElContrato() {
        AppConfig c = AppConfig.DEFAULTS;
        assertTrue(c.isCameraEnabled());
        assertTrue(c.isGalleryEnabled());
        assertTrue(c.isHistoryEnabled());
        assertTrue(c.isHistorySaveEnabled());
        assertTrue(c.isExportEnabled());
        assertTrue(c.isSaveToGalleryEnabled());
        assertTrue(c.isAboutEnabled());
        assertTrue(c.isTipsEnabled());
        assertTrue(c.isShowApiStatus());
        assertFalse(c.isCloudSyncEnabled());
        assertEquals(Collections.emptyList(), c.getAnnouncements());
        assertTrue(c.isRegistrationEnabled());
        assertFalse(c.isMaintenanceMode());
        assertEquals("Estamos realizando tareas de mantenimiento. Vuelve a intentarlo más tarde.",
                c.getMaintenanceMessage());
        assertEquals("1.0", c.getMinAppVersion());
        assertEquals(0.3f, c.getScoreThreshold(), DELTA);
        assertTrue(c.isNmsEnabled());
        assertEquals(0.3f, c.getNmsIou(), DELTA);
        assertEquals(100, c.getMaxDetections());
        assertEquals(60, c.getRequestTimeoutS());
        assertEquals(0, c.getRetries());
    }

    @Test
    public void nmsIouPorDefecto_igualAlDelModelo() {
        assertEquals(RespuestaPrediccion.NMS_IOU_DEFAULT, AppConfig.DEFAULTS.getNmsIou(), DELTA);
    }

    @Test
    public void jsonNuloVacioOInvalido_devuelveDefaults() {
        assertSame(AppConfig.DEFAULTS, AppConfig.fromJson(null));
        assertSame(AppConfig.DEFAULTS, AppConfig.fromJson(""));
        assertSame(AppConfig.DEFAULTS, AppConfig.fromJson("   "));
        assertSame(AppConfig.DEFAULTS, AppConfig.fromJson("{no es json"));
        assertSame(AppConfig.DEFAULTS, AppConfig.fromJson("[1,2,3]"));
        assertSame(AppConfig.DEFAULTS, AppConfig.fromJson("true"));
    }

    @Test
    public void objetoVacio_usaDefaults() {
        AppConfig c = AppConfig.fromJson("{}");
        assertTrue(c.isCameraEnabled());
        assertEquals(100, c.getMaxDetections());
    }

    // --- Lectura tolerante ---

    @Test
    public void clavesDesconocidasYRetiradas_seIgnoran() {
        AppConfig c = AppConfig.fromJson("{\"guest_mode_enabled\":false,"
                + "\"hidden_classes\":[\"Metal\"],\"clave_nueva\":123,\"camera_enabled\":false}");
        assertFalse(c.isCameraEnabled());
        assertTrue(c.isGalleryEnabled());
    }

    @Test
    public void tipoIncorrecto_usaDefault() {
        AppConfig c = AppConfig.fromJson("{\"camera_enabled\":\"false\",\"max_detections\":\"5\","
                + "\"score_threshold\":true,\"announcement_1\":42,\"history_enabled\":null}");
        assertTrue(c.isCameraEnabled());
        assertEquals(100, c.getMaxDetections());
        assertEquals(0.3f, c.getScoreThreshold(), DELTA);
        assertTrue(c.isHistoryEnabled());
    }

    @Test
    public void valoresValidos_seLeen() {
        AppConfig c = AppConfig.fromJson("{\"score_threshold\":0.55,\"nms_enabled\":false,"
                + "\"nms_iou\":0.5,\"max_detections\":10,\"request_timeout_s\":30,\"retries\":2,"
                + "\"maintenance_mode\":true,\"maintenance_message\":\"  Volvemos pronto  \","
                + "\"min_app_version\":\"1.2.3\",\"registration_enabled\":false}");
        assertEquals(0.55f, c.getScoreThreshold(), DELTA);
        assertFalse(c.isNmsEnabled());
        assertEquals(0.5f, c.getNmsIou(), DELTA);
        assertEquals(10, c.getMaxDetections());
        assertEquals(30, c.getRequestTimeoutS());
        assertEquals(2, c.getRetries());
        assertTrue(c.isMaintenanceMode());
        assertEquals("Volvemos pronto", c.getMaintenanceMessage());
        assertEquals("1.2.3", c.getMinAppVersion());
        assertFalse(c.isRegistrationEnabled());
    }

    @Test
    public void numerosFueraDeRango_seRecortan() {
        AppConfig bajo = AppConfig.fromJson("{\"score_threshold\":0.2,\"nms_iou\":-1,"
                + "\"max_detections\":1,\"request_timeout_s\":1,\"retries\":-3}");
        assertEquals(0.3f, bajo.getScoreThreshold(), DELTA);
        assertEquals(0f, bajo.getNmsIou(), DELTA);
        assertEquals(3, bajo.getMaxDetections());
        assertEquals(5, bajo.getRequestTimeoutS());
        assertEquals(0, bajo.getRetries());

        AppConfig alto = AppConfig.fromJson("{\"score_threshold\":7,\"nms_iou\":2,"
                + "\"max_detections\":500,\"request_timeout_s\":999,\"retries\":9}");
        assertEquals(1f, alto.getScoreThreshold(), DELTA);
        assertEquals(1f, alto.getNmsIou(), DELTA);
        assertEquals(100, alto.getMaxDetections());
        assertEquals(120, alto.getRequestTimeoutS());
        assertEquals(5, alto.getRetries());
    }

    @Test
    public void enteroConDecimales_usaDefault() {
        AppConfig c = AppConfig.fromJson("{\"max_detections\":10.5,\"retries\":2.0}");
        assertEquals(100, c.getMaxDetections());
        assertEquals(2, c.getRetries());
    }

    @Test
    public void versionMinimaConFormatoInvalido_usaDefault() {
        assertEquals("1.0", AppConfig.fromJson("{\"min_app_version\":\"v2\"}").getMinAppVersion());
        assertEquals("1.0", AppConfig.fromJson("{\"min_app_version\":\"1.2.3.4.5\"}").getMinAppVersion());
        assertEquals("1.0", AppConfig.fromJson("{\"min_app_version\":\"\"}").getMinAppVersion());
        assertEquals("2", AppConfig.fromJson("{\"min_app_version\":\"2\"}").getMinAppVersion());
    }

    @Test
    public void mensajeDeMantenimientoVacio_usaDefault() {
        AppConfig c = AppConfig.fromJson("{\"maintenance_message\":\"   \"}");
        assertEquals(AppConfig.DEFAULT_MAINTENANCE_MESSAGE, c.getMaintenanceMessage());
    }

    // --- Dependencias entre flags ---

    @Test
    public void historialDesactivado_desactivaGuardarYExportar() {
        AppConfig c = AppConfig.fromJson("{\"history_enabled\":false,"
                + "\"history_save_enabled\":true,\"export_enabled\":true}");
        assertFalse(c.isHistoryEnabled());
        assertFalse(c.isHistorySaveEnabled());
        assertFalse(c.isExportEnabled());
    }

    @Test
    public void historialActivado_respetaGuardarYExportar() {
        AppConfig c = AppConfig.fromJson("{\"history_save_enabled\":false,\"export_enabled\":true}");
        assertTrue(c.isHistoryEnabled());
        assertFalse(c.isHistorySaveEnabled());
        assertTrue(c.isExportEnabled());
    }

    // --- Anuncios ---

    @Test
    public void anuncios_enOrdenYSinBloquesVacios() {
        AppConfig c = AppConfig.fromJson("{\"announcement_1\":\" Hola \","
                + "\"announcement_2\":\"   \",\"announcement_3\":\"https://ejemplo.org\"}");
        assertEquals(Arrays.asList("Hola", "https://ejemplo.org"), c.getAnnouncements());
    }

    @Test
    public void anunciosDesactivados_noDevuelveNinguno() {
        AppConfig c = AppConfig.fromJson("{\"announcements_enabled\":false,"
                + "\"announcement_1\":\"Hola\"}");
        assertTrue(c.getAnnouncements().isEmpty());
    }

    @Test
    public void anuncioLargo_seRecortaA500() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 600; i++) sb.append('a');
        AppConfig c = AppConfig.fromJson("{\"announcement_1\":\"" + sb + "\"}");
        assertEquals(AppConfig.ANNOUNCEMENT_MAX_LEN, c.getAnnouncements().get(0).length());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void anuncios_listaInmutable() {
        AppConfig.fromJson("{\"announcement_1\":\"Hola\"}").getAnnouncements().add("x");
    }
}
