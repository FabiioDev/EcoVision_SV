package com.irkana.ecovisionsv.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.irkana.ecovisionsv.network.ErrorDeteccion.Tipo;

import org.junit.Test;

public class ErrorDeteccionTest {

    private static ErrorDeteccion e(int codigo, String cuerpo) {
        return ErrorDeteccion.desdeRespuesta(codigo, cuerpo);
    }

    @Test
    public void limiteYMantenimiento_usanElDetalleDelBackend() {
        ErrorDeteccion limite = e(429, "{\"detail\":\"Espera 12 s e inténtalo de nuevo.\"}");
        assertEquals(Tipo.LIMITE, limite.tipo);
        assertEquals("Espera 12 s e inténtalo de nuevo.", limite.detalle);

        ErrorDeteccion mant = e(503, "{\"detail\":\"Volvemos pronto\"}");
        assertEquals(Tipo.NO_DISPONIBLE, mant.tipo);
        assertEquals("Volvemos pronto", mant.detalle);
    }

    @Test
    public void sesionYCuenta_textoFijo() {
        assertEquals(Tipo.SESION, e(401, "{\"detail\":\"token inválido\"}").tipo);
        assertNull(e(401, "{\"detail\":\"token inválido\"}").detalle);
        assertEquals(Tipo.CUENTA_DESACTIVADA, e(403, "{\"detail\":\"usuario desactivado\"}").tipo);
    }

    @Test
    public void imagen() {
        assertEquals(Tipo.IMAGEN_GRANDE, e(413, null).tipo);
        assertEquals(Tipo.FORMATO, e(415, null).tipo);
        ErrorDeteccion ilegible = e(422, "{\"detail\":\"Imagen ilegible\"}");
        assertEquals(Tipo.IMAGEN_RECHAZADA, ilegible.tipo);
        assertEquals("Imagen ilegible", ilegible.detalle);
        // 422 de validación de FastAPI: detail es una lista → texto fijo
        assertNull(e(422, "{\"detail\":[{\"loc\":[\"body\",\"file\"]}]}").detalle);
    }

    @Test
    public void modelo() {
        assertEquals(Tipo.MODELO, e(502, "{\"detail\":\"No se pudo contactar el modelo.\"}").tipo);
        assertEquals(Tipo.MODELO_LENTO, e(504, null).tipo);
        assertEquals(Tipo.MODELO_LENTO, ErrorDeteccion.timeout().tipo);
    }

    @Test
    public void otrosCodigosYCuerposRaros() {
        assertEquals(Tipo.SERVIDOR, e(500, "<html>").tipo);
        assertEquals(500, e(500, null).codigo);
        assertNull(ErrorDeteccion.detail("no es json"));
        assertNull(ErrorDeteccion.detail("{\"detail\":\"   \"}"));
        assertNull(ErrorDeteccion.detail("[1]"));
    }
}
