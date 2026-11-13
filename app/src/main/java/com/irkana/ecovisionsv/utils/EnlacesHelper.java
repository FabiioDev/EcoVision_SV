package com.irkana.ecovisionsv.utils;

import android.annotation.SuppressLint;
import android.text.Layout;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.view.MotionEvent;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Enlaces en textos planos de la config (anuncios). Solo las direcciones https:// se
 * convierten en enlace; http:// y otros esquemas se dejan como texto.
 *
 * Para que los enlaces largos no ocupen el anuncio:
 *  - {@code [texto](https://...)} muestra solo "texto" como enlace (formato Markdown);
 *  - una dirección suelta se muestra abreviada ({@link #abreviar}), p. ej.
 *    "github.com/…/EcoVisionSV-1.0.0.apk", y abre la dirección completa.
 */
public final class EnlacesHelper {

    /** https:// seguido de caracteres sin espacios; no termina en puntuación de cierre. */
    private static final Pattern HTTPS = Pattern.compile(
            "https://[^\\s<>\"]*[^\\s<>\".,;:!?)\\]]", Pattern.CASE_INSENSITIVE);

    /** [texto](https://...): texto de 1 a 80 caracteres sin saltos ni corchetes. */
    private static final Pattern MARKDOWN = Pattern.compile(
            "\\[([^\\[\\]\\n]{1,80})\\]\\((https://[^\\s()<>\"]+)\\)", Pattern.CASE_INSENSITIVE);

    /** Hasta este largo (sin "https://") una dirección se muestra entera. */
    static final int LARGO_MAX_VISIBLE = 30;
    /** Último tramo de la ruta que se conserva al abreviar (p. ej. el nombre del archivo). */
    static final int LARGO_MAX_FINAL = 24;

    private EnlacesHelper() {}

    /** Un enlace del texto ya formateado: posiciones [inicio, fin) y dirección completa. */
    public static final class Enlace {
        public final int inicio;
        public final int fin;
        public final String url;

        Enlace(int inicio, int fin, String url) {
            this.inicio = inicio;
            this.fin = fin;
            this.url = url;
        }
    }

    /** Texto visible (con los enlaces abreviados o con su texto) y dónde está cada enlace. */
    public static final class Formateado {
        public final String texto;
        public final List<Enlace> enlaces;

        Formateado(String texto, List<Enlace> enlaces) {
            this.texto = texto;
            this.enlaces = Collections.unmodifiableList(enlaces);
        }
    }

    /** Posiciones [inicio, fin) de cada enlace https:// del texto ORIGINAL, en orden. */
    public static List<int[]> buscarEnlaces(String texto) {
        List<int[]> enlaces = new ArrayList<>();
        if (texto == null) return enlaces;
        Matcher m = HTTPS.matcher(texto);
        while (m.find()) enlaces.add(new int[]{m.start(), m.end()});
        return enlaces;
    }

    /**
     * Sustituye {@code [texto](https://...)} por "texto" y abrevia las direcciones sueltas.
     * Sin dependencias de Android (se prueba con JUnit).
     */
    public static Formateado formatear(String original) {
        String texto = original == null ? "" : original;
        StringBuilder sb = new StringBuilder();
        List<Enlace> enlaces = new ArrayList<>();
        Matcher md = MARKDOWN.matcher(texto);
        int desde = 0;
        while (md.find()) {
            sueltos(texto.substring(desde, md.start()), sb, enlaces);
            String etiqueta = md.group(1).trim();
            if (etiqueta.isEmpty()) etiqueta = abreviar(md.group(2));
            int inicio = sb.length();
            sb.append(etiqueta);
            enlaces.add(new Enlace(inicio, sb.length(), md.group(2)));
            desde = md.end();
        }
        sueltos(texto.substring(desde), sb, enlaces);
        return new Formateado(sb.toString(), enlaces);
    }

    /** Copia un tramo sin Markdown, abreviando sus direcciones https://. */
    private static void sueltos(String tramo, StringBuilder sb, List<Enlace> enlaces) {
        Matcher m = HTTPS.matcher(tramo);
        int desde = 0;
        while (m.find()) {
            sb.append(tramo, desde, m.start());
            int inicio = sb.length();
            sb.append(abreviar(m.group()));
            enlaces.add(new Enlace(inicio, sb.length(), m.group()));
            desde = m.end();
        }
        sb.append(tramo.substring(desde));
    }

    /**
     * Forma corta de una dirección para mostrarla: sin "https://" ni "www."; si sigue siendo
     * larga, el dominio y el último tramo de la ruta ("github.com/…/archivo.apk") o solo el
     * dominio ("docs.google.com/…").
     */
    public static String abreviar(String url) {
        String s = url.replaceFirst("(?i)^https://", "").replaceFirst("(?i)^www\\.", "");
        // Sin parámetros ni fragmento en lo que se muestra
        int corte = indiceDe(s, '?', '#');
        String visible = corte >= 0 ? s.substring(0, corte) : s;
        while (visible.endsWith("/")) visible = visible.substring(0, visible.length() - 1);
        if (visible.length() <= LARGO_MAX_VISIBLE && corte < 0) return visible;

        int barra = visible.indexOf('/');
        if (barra < 0) return visible + (corte >= 0 ? "/…" : "");
        String dominio = visible.substring(0, barra);
        String ultimo = visible.substring(visible.lastIndexOf('/') + 1);
        boolean unSoloTramo = visible.indexOf('/') == visible.lastIndexOf('/');
        if (visible.length() <= LARGO_MAX_VISIBLE) return visible + "…";
        if (!ultimo.isEmpty() && ultimo.length() <= LARGO_MAX_FINAL) {
            return unSoloTramo ? dominio + "/" + ultimo + (corte >= 0 ? "…" : "")
                    : dominio + "/…/" + ultimo;
        }
        return dominio + "/…";
    }

    private static int indiceDe(String s, char a, char b) {
        int i = s.indexOf(a), j = s.indexOf(b);
        if (i < 0) return j;
        if (j < 0) return i;
        return Math.min(i, j);
    }

    /** Texto formateado con los enlaces marcados (se abren en el navegador al pulsarlos). */
    public static SpannableString conEnlaces(String texto) {
        Formateado f = formatear(texto);
        SpannableString s = new SpannableString(f.texto);
        for (Enlace e : f.enlaces) {
            s.setSpan(new URLSpan(e.url), e.inicio, e.fin, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return s;
    }

    /**
     * Hace que los enlaces de un TextView se puedan pulsar SIN capturar el resto de toques:
     * fuera de un enlace, el toque llega al contenedor (p. ej. para abrir el anuncio) y el
     * deslizamiento horizontal del carrusel sigue funcionando.
     * (LinkMovementMethod capturaría todos los toques del TextView.)
     */
    @SuppressLint("ClickableViewAccessibility")
    public static void enlacesPulsables(TextView tv) {
        tv.setOnTouchListener((v, e) -> {
            ClickableSpan span = spanBajoElDedo(tv, e);
            if (span == null) return false;
            if (e.getActionMasked() == MotionEvent.ACTION_UP) span.onClick(tv);
            return true;
        });
    }

    private static ClickableSpan spanBajoElDedo(TextView tv, MotionEvent e) {
        CharSequence texto = tv.getText();
        Layout layout = tv.getLayout();
        if (!(texto instanceof Spanned) || layout == null) return null;
        int x = (int) e.getX() - tv.getTotalPaddingLeft() + tv.getScrollX();
        int y = (int) e.getY() - tv.getTotalPaddingTop() + tv.getScrollY();
        if (y < 0 || y > layout.getHeight()) return null;
        int linea = layout.getLineForVertical(y);
        if (x < layout.getLineLeft(linea) || x > layout.getLineRight(linea)) return null;
        int offset = layout.getOffsetForHorizontal(linea, x);
        ClickableSpan[] spans = ((Spanned) texto).getSpans(offset, offset, ClickableSpan.class);
        return spans.length > 0 ? spans[0] : null;
    }
}
