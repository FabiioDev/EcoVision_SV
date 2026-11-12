package com.irkana.ecovisionsv.model;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** Pruebas del filtro NMS de {@link RespuestaPrediccion}. */
public class RespuestaPrediccionTest {

    private static Deteccion det(String categoria, float confianza, Float... bbox) {
        Deteccion d = new Deteccion();
        d.categoria = categoria;
        d.confianza = confianza;
        d.bboxNorm = bbox.length == 0 ? null : Arrays.asList(bbox);
        return d;
    }

    private static RespuestaPrediccion respuesta(Deteccion... ds) {
        RespuestaPrediccion r = new RespuestaPrediccion();
        r.detecciones = new ArrayList<>(Arrays.asList(ds));
        return r;
    }

    @Test
    public void solapadas_seConservaLaDeMayorConfianza() {
        Deteccion baja = det("Plastico", 0.6f, 0.10f, 0.10f, 0.50f, 0.50f);
        Deteccion alta = det("Plastico", 0.9f, 0.12f, 0.12f, 0.52f, 0.52f);

        List<Deteccion> res = respuesta(baja, alta).deteccionesFiltradas();

        assertEquals(1, res.size());
        assertSame(alta, res.get(0));
    }

    @Test
    public void disjuntas_seConservanAmbasOrdenadas() {
        Deteccion a = det("Metal", 0.5f, 0.0f, 0.0f, 0.2f, 0.2f);
        Deteccion b = det("Papel", 0.8f, 0.6f, 0.6f, 0.9f, 0.9f);

        List<Deteccion> res = respuesta(a, b).deteccionesFiltradas();

        assertEquals(2, res.size());
        assertSame(b, res.get(0));
        assertSame(a, res.get(1));
    }

    @Test
    public void solapamientoBajoElUmbral_noSeSuprime() {
        // IoU = 0.04 / (0.16 + 0.16 - 0.04) ≈ 0.14 < 0.3
        Deteccion a = det("Vidrio", 0.9f, 0.0f, 0.0f, 0.4f, 0.4f);
        Deteccion b = det("Vidrio", 0.7f, 0.2f, 0.2f, 0.6f, 0.6f);

        assertEquals(2, respuesta(a, b).deteccionesFiltradas().size());
    }

    @Test
    public void iouEntre03y045_ahoraSeSuprime() {
        // IoU = 0.09 / (0.16 + 0.16 - 0.09) ≈ 0.39: con 0.45 se conservaban ambas
        Deteccion a = det("Vidrio", 0.9f, 0.0f, 0.0f, 0.4f, 0.4f);
        Deteccion b = det("Vidrio", 0.7f, 0.1f, 0.1f, 0.5f, 0.5f);

        List<Deteccion> res = respuesta(a, b).deteccionesFiltradas();

        assertEquals(1, res.size());
        assertSame(a, res.get(0));
    }

    @Test
    public void umbralCero_eliminaCualquierSolapamientoPeroNoLasDisjuntas() {
        Deteccion a = det("Metal", 0.9f, 0.0f, 0.0f, 0.4f, 0.4f);
        Deteccion b = det("Metal", 0.8f, 0.39f, 0.39f, 0.6f, 0.6f);  // se toca apenas con a
        Deteccion c = det("Papel", 0.7f, 0.7f, 0.7f, 0.9f, 0.9f);    // no se toca con nadie

        List<Deteccion> res = respuesta(a, b, c).deteccionesFiltradas(0f);

        assertEquals(2, res.size());
        assertSame(a, res.get(0));
        assertSame(c, res.get(1));
    }

    @Test
    public void umbralUno_noEliminaNinguna() {
        Deteccion a = det("Plastico", 0.9f, 0.1f, 0.1f, 0.5f, 0.5f);
        Deteccion b = det("Plastico", 0.8f, 0.1f, 0.1f, 0.5f, 0.5f);  // idéntica

        assertEquals(2, respuesta(a, b).deteccionesFiltradas(1f).size());
    }

    @Test
    public void bboxInvalida_noSeSuprime() {
        Deteccion a = det("Metal", 0.9f, 0.1f, 0.1f, 0.5f, 0.5f);
        Deteccion b = det("Metal", 0.7f);

        assertEquals(2, respuesta(a, b).deteccionesFiltradas().size());
    }

    @Test
    public void sinDetecciones_devuelveListaVacia() {
        RespuestaPrediccion r = new RespuestaPrediccion();
        assertTrue(r.deteccionesFiltradas().isEmpty());
        r.detecciones = new ArrayList<>();
        assertTrue(r.deteccionesFiltradas().isEmpty());
    }

    // --- Filtro con la config (nms_enabled, nms_iou) ---

    @Test
    public void filtrar_conNmsPorDefecto_igualQueAntes() {
        Deteccion baja = det("Plastico", 0.6f, 0.10f, 0.10f, 0.50f, 0.50f);
        Deteccion alta = det("Plastico", 0.9f, 0.12f, 0.12f, 0.52f, 0.52f);
        RespuestaPrediccion r = respuesta(baja, alta);

        RespuestaPrediccion f = r.filtrar(true, RespuestaPrediccion.NMS_IOU_DEFAULT, 100);

        assertEquals(r.deteccionesFiltradas(), f.deteccionesFiltradas());
        assertEquals(Boolean.TRUE, f.filtradoApp);
    }

    @Test
    public void filtrar_sinNms_conservaTodasOrdenadas() {
        Deteccion baja = det("Plastico", 0.6f, 0.10f, 0.10f, 0.50f, 0.50f);
        Deteccion alta = det("Plastico", 0.9f, 0.12f, 0.12f, 0.52f, 0.52f);

        List<Deteccion> res = respuesta(baja, alta).filtrar(false, 0.3f, 100).deteccionesFiltradas();

        assertEquals(Arrays.asList(alta, baja), res);
    }

    @Test
    public void filtrada_noSeVuelveAFiltrarConElDefecto() {
        // IoU = 0.16 / 0.34 ≈ 0.47: con nms_iou=0.5 se conservan ambas aunque el defecto (0.3) suprimiría una
        Deteccion a = det("Vidrio", 0.9f, 0.0f, 0.0f, 0.5f, 0.5f);
        Deteccion b = det("Vidrio", 0.7f, 0.1f, 0.1f, 0.6f, 0.6f);
        RespuestaPrediccion r = respuesta(a, b);

        assertEquals(1, r.deteccionesFiltradas().size());
        RespuestaPrediccion f = r.filtrar(true, 0.5f, 100);
        assertEquals(2, f.deteccionesFiltradas().size());

        // Tras guardarla en el historial (JSON) se lee igual
        com.google.gson.Gson gson = new com.google.gson.Gson();
        RespuestaPrediccion leida = gson.fromJson(gson.toJson(f), RespuestaPrediccion.class);
        assertEquals(2, leida.deteccionesFiltradas().size());
    }

    @Test
    public void entradaAntigua_sinMarca_usaNmsPorDefecto() {
        com.google.gson.Gson gson = new com.google.gson.Gson();
        RespuestaPrediccion r = gson.fromJson("{\"detecciones\":["
                + "{\"categoria\":\"Vidrio\",\"confianza\":0.9,\"bbox_norm\":[0,0,0.5,0.5]},"
                + "{\"categoria\":\"Vidrio\",\"confianza\":0.7,\"bbox_norm\":[0.1,0.1,0.6,0.6]}]}",
                RespuestaPrediccion.class);
        assertEquals(null, r.filtradoApp);
        assertEquals(1, r.deteccionesFiltradas().size());
    }

    @Test
    public void filtrar_maxDetecciones_conservaLasDeMayorConfianzaTrasElNms() {
        Deteccion solapadaAlta = det("Metal", 0.95f, 0.10f, 0.10f, 0.30f, 0.30f);
        Deteccion solapadaBaja = det("Metal", 0.90f, 0.11f, 0.11f, 0.31f, 0.31f); // la quita el NMS
        Deteccion b = det("Papel", 0.80f, 0.40f, 0.40f, 0.50f, 0.50f);
        Deteccion c = det("Vidrio", 0.70f, 0.60f, 0.60f, 0.70f, 0.70f);
        Deteccion d = det("Carton", 0.60f, 0.80f, 0.80f, 0.90f, 0.90f);

        List<Deteccion> res = respuesta(d, c, b, solapadaBaja, solapadaAlta)
                .filtrar(true, 0.3f, 3).deteccionesFiltradas();

        assertEquals(Arrays.asList(solapadaAlta, b, c), res);
    }

    @Test
    public void filtrar_menosQueElMaximo_noRecorta() {
        Deteccion a = det("Metal", 0.5f, 0.0f, 0.0f, 0.2f, 0.2f);
        assertEquals(1, respuesta(a).filtrar(true, 0.3f, 3).deteccionesFiltradas().size());
    }
}
