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
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Enlaces en textos planos de la config (anuncios). Solo las direcciones https:// se
 * convierten en enlace; http:// y otros esquemas se dejan como texto.
 */
public final class EnlacesHelper {

    /** https:// seguido de caracteres sin espacios; no termina en puntuación de cierre. */
    private static final Pattern HTTPS = Pattern.compile(
            "https://[^\\s<>\"]*[^\\s<>\".,;:!?)\\]]", Pattern.CASE_INSENSITIVE);

    private EnlacesHelper() {}

    /** Posiciones [inicio, fin) de cada enlace https:// del texto, en orden. */
    public static List<int[]> buscarEnlaces(String texto) {
        List<int[]> enlaces = new ArrayList<>();
        if (texto == null) return enlaces;
        Matcher m = HTTPS.matcher(texto);
        while (m.find()) enlaces.add(new int[]{m.start(), m.end()});
        return enlaces;
    }

    /** Texto con los enlaces https:// marcados (se abren en el navegador al pulsarlos). */
    public static SpannableString conEnlaces(String texto) {
        SpannableString s = new SpannableString(texto == null ? "" : texto);
        for (int[] e : buscarEnlaces(texto)) {
            s.setSpan(new URLSpan(texto.substring(e[0], e[1])), e[0], e[1],
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
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
