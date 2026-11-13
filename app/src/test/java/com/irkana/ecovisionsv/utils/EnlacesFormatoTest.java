package com.irkana.ecovisionsv.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

/** Enlaces abreviados y [texto](https://...) en los anuncios. */
public class EnlacesFormatoTest {

    private static final String APK =
            "https://github.com/FabiioDev/EcoVision_SV/releases/download/v1.0.0/EcoVisionSV-1.0.0.apk";
    private static final String FORM =
            "https://docs.google.com/forms/d/e/1FAIpQLSdEjemploDeFormularioMuyLargo/viewform?usp=sf_link";

    @Test
    public void abreviar_direccionesCortasEnteras() {
        assertEquals("forms.gle/abc", EnlacesHelper.abreviar("https://forms.gle/abc"));
        assertEquals("ejemplo.com", EnlacesHelper.abreviar("https://www.ejemplo.com/"));
    }

    @Test
    public void abreviar_largas_dominioYUltimoTramo() {
        assertEquals("github.com/…/EcoVisionSV-1.0.0.apk", EnlacesHelper.abreviar(APK));
        assertEquals("docs.google.com/…/viewform", EnlacesHelper.abreviar(FORM));
    }

    @Test
    public void abreviar_sinUltimoTramoCorto_soloDominio() {
        assertEquals("ejemplo.com/…", EnlacesHelper.abreviar(
                "https://ejemplo.com/a/un-tramo-final-demasiado-largo-para-mostrarlo"));
    }

    @Test
    public void abreviar_ocultaParametros() {
        assertEquals("ejemplo.com/a…", EnlacesHelper.abreviar("https://ejemplo.com/a?x=1"));
    }

    @Test
    public void markdown_muestraSoloElTexto() {
        EnlacesHelper.Formateado f = EnlacesHelper.formatear(
                "Nueva versión: [Descarga la app](" + APK + "). ¡Pruébala!");
        assertEquals("Nueva versión: Descarga la app. ¡Pruébala!", f.texto);
        assertEquals(1, f.enlaces.size());
        EnlacesHelper.Enlace e = f.enlaces.get(0);
        assertEquals("Descarga la app", f.texto.substring(e.inicio, e.fin));
        assertEquals(APK, e.url);
    }

    @Test
    public void direccionSuelta_seAbreviaPeroAbreLaCompleta() {
        EnlacesHelper.Formateado f = EnlacesHelper.formatear("Opina en " + FORM + ", gracias");
        assertEquals("Opina en docs.google.com/…/viewform, gracias", f.texto);
        EnlacesHelper.Enlace e = f.enlaces.get(0);
        assertEquals("docs.google.com/…/viewform", f.texto.substring(e.inicio, e.fin));
        assertEquals(FORM, e.url);
    }

    @Test
    public void mezclaDeMarkdownYSueltos_enOrden() {
        EnlacesHelper.Formateado f = EnlacesHelper.formatear(
                "[App](" + APK + ") y https://forms.gle/abc");
        assertEquals("App y forms.gle/abc", f.texto);
        List<EnlacesHelper.Enlace> e = f.enlaces;
        assertEquals(2, e.size());
        assertEquals(APK, e.get(0).url);
        assertEquals("https://forms.gle/abc", e.get(1).url);
        assertEquals("forms.gle/abc", f.texto.substring(e.get(1).inicio, e.get(1).fin));
    }

    @Test
    public void markdownConHttpUOtroEsquema_quedaComoTexto() {
        String t = "[Pulsa](http://inseguro.com) y [x](javascript:alert(1))";
        EnlacesHelper.Formateado f = EnlacesHelper.formatear(t);
        assertEquals(t, f.texto);
        assertTrue(f.enlaces.isEmpty());
    }

    @Test
    public void textoSinEnlacesONulo() {
        assertEquals("Hola", EnlacesHelper.formatear("Hola").texto);
        assertEquals("", EnlacesHelper.formatear(null).texto);
        assertTrue(EnlacesHelper.formatear(null).enlaces.isEmpty());
    }
}
