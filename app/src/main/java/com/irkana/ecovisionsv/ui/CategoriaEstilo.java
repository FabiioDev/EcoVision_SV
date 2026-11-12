package com.irkana.ecovisionsv.ui;

import android.content.res.ColorStateList;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.irkana.ecovisionsv.R;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Color fijo y nombre visible de cada clase del modelo (6 clases TACO).
 * Lo usan el chip del historial y el chip del carrusel de resultados, para que
 * una categoría tenga siempre el mismo color en toda la app.
 * Las cajas (bounding boxes) siguen coloreándose por orden de detección.
 */
final class CategoriaEstilo {

    private CategoriaEstilo() {}

    private enum Clase {
        PLASTICO("Plástico", R.color.categoria_plastico_fondo, R.color.categoria_plastico_texto),
        METAL("Metal", R.color.categoria_metal_fondo, R.color.categoria_metal_texto),
        PAPEL("Papel", R.color.categoria_papel_fondo, R.color.categoria_papel_texto),
        CARTON("Cartón", R.color.categoria_carton_fondo, R.color.categoria_carton_texto),
        VIDRIO("Vidrio", R.color.categoria_vidrio_fondo, R.color.categoria_vidrio_texto),
        ORGANICO("Orgánico", R.color.categoria_organico_fondo, R.color.categoria_organico_texto);

        final String nombre;
        @ColorRes final int fondo;
        @ColorRes final int texto;

        Clase(String nombre, @ColorRes int fondo, @ColorRes int texto) {
            this.nombre = nombre;
            this.fondo = fondo;
            this.texto = texto;
        }
    }

    /** "Plastico", "plástico" o "PLASTICO" → PLASTICO; null si no es una clase conocida. */
    @Nullable
    private static Clase clase(@Nullable String categoria) {
        if (categoria == null) return null;
        String clave = Normalizer.normalize(categoria.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT);
        for (Clase c : Clase.values()) {
            if (c.name().equals(clave)) return c;
        }
        return null;
    }

    /** Nombre con tildes para mostrar; si la clase no se reconoce, el texto tal cual. */
    @NonNull
    static String nombre(@Nullable String categoria) {
        Clase c = clase(categoria);
        if (c != null) return c.nombre;
        return categoria != null ? categoria : "—";
    }

    /** Pone el nombre y los colores de la categoría en un chip con fondo @drawable/bg_chip_categoria. */
    static void aplicarChip(@NonNull TextView chip, @Nullable String categoria) {
        Clase c = clase(categoria);
        int fondo = c != null ? c.fondo : R.color.categoria_otro_fondo;
        int texto = c != null ? c.texto : R.color.categoria_otro_texto;
        chip.setText(nombre(categoria));
        chip.setBackgroundTintList(
                ColorStateList.valueOf(ContextCompat.getColor(chip.getContext(), fondo)));
        chip.setTextColor(ContextCompat.getColor(chip.getContext(), texto));
    }
}
