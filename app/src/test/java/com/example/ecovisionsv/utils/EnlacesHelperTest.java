package com.example.ecovisionsv.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class EnlacesHelperTest {

    private static List<String> enlaces(String texto) {
        List<String> r = new ArrayList<>();
        for (int[] e : EnlacesHelper.buscarEnlaces(texto)) r.add(texto.substring(e[0], e[1]));
        return r;
    }

    @Test
    public void detectaHttpsYQuitaLaPuntuacionFinal() {
        assertEquals(List.of("https://example.com/descarga"),
                enlaces("Descárgala en https://example.com/descarga."));
        assertEquals(List.of("https://forms.gle/abc"),
                enlaces("Opina (https://forms.gle/abc), gracias"));
    }

    @Test
    public void variosEnlaces_enOrden() {
        assertEquals(List.of("https://a.org", "https://b.org/x?y=1"),
                enlaces("Ver https://a.org y https://b.org/x?y=1!"));
    }

    @Test
    public void httpYOtrosEsquemas_noSonEnlaces() {
        assertTrue(enlaces("http://inseguro.com ftp://x.org www.sin-esquema.com").isEmpty());
        assertTrue(enlaces("javascript:alert(1)").isEmpty());
    }

    @Test
    public void textoNuloOVacio_sinEnlaces() {
        assertTrue(EnlacesHelper.buscarEnlaces(null).isEmpty());
        assertTrue(EnlacesHelper.buscarEnlaces("").isEmpty());
        assertTrue(enlaces("https://").isEmpty());
    }
}
