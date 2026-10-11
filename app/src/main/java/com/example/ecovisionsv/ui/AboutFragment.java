package com.example.ecovisionsv.ui;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.ecovisionsv.R;

/**
 * Pantalla "Acerca de": descripción del proyecto, tecnologías, modelo y créditos.
 */
public class AboutFragment extends Fragment {

    /** Etapa del proyecto a la que pertenece cada tecnología; define sus colores. */
    private enum Etapa {
        ENTRENAMIENTO(R.string.tech_etapa_entrenamiento, R.color.tertiary_fixed, R.color.on_tertiary_fixed_variant),
        SERVIDOR(R.string.tech_etapa_servidor, R.color.secondary_fixed, R.color.secondary),
        APP(R.string.tech_etapa_app, R.color.primary_fixed, R.color.primary);

        @StringRes final int nombre;
        @ColorRes final int fondo;
        @ColorRes final int color;

        Etapa(int nombre, int fondo, int color) {
            this.nombre = nombre;
            this.fondo = fondo;
            this.color = color;
        }
    }

    private static final class Tecnologia {
        @DrawableRes final int icono;
        @StringRes final int nombre;
        @StringRes final int descripcion;
        final Etapa etapa;

        Tecnologia(int icono, int nombre, int descripcion, Etapa etapa) {
            this.icono = icono;
            this.nombre = nombre;
            this.descripcion = descripcion;
            this.etapa = etapa;
        }
    }

    /** Ordenadas según el flujo: entrenamiento → servidor → app. */
    private static final Tecnologia[] TECNOLOGIAS = {
            new Tecnologia(R.drawable.ic_code, R.string.tech_colab_nombre,
                    R.string.tech_colab_descripcion, Etapa.ENTRENAMIENTO),
            new Tecnologia(R.drawable.ic_memory, R.string.tech_tensorflow_nombre,
                    R.string.tech_tensorflow_descripcion, Etapa.ENTRENAMIENTO),
            new Tecnologia(R.drawable.ic_api, R.string.tech_fastapi_nombre,
                    R.string.tech_fastapi_descripcion, Etapa.SERVIDOR),
            new Tecnologia(R.drawable.ic_cloud, R.string.tech_cloudrun_nombre,
                    R.string.tech_cloudrun_descripcion, Etapa.SERVIDOR),
            new Tecnologia(R.drawable.ic_android, R.string.tech_android_nombre,
                    R.string.tech_android_descripcion, Etapa.APP),
            new Tecnologia(R.drawable.ic_camera, R.string.tech_camerax_nombre,
                    R.string.tech_camerax_descripcion, Etapa.APP),
            new Tecnologia(R.drawable.ic_sync_alt, R.string.tech_retrofit_nombre,
                    R.string.tech_retrofit_descripcion, Etapa.APP),
            new Tecnologia(R.drawable.ic_storage, R.string.tech_room_nombre,
                    R.string.tech_room_descripcion, Etapa.APP),
    };

    public AboutFragment() {
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_about, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        llenarTecnologias(view.findViewById(R.id.contenedor_tecnologias));
        view.findViewById(R.id.enlace_github).setOnClickListener(v -> abrirRepositorio());
    }

    private void llenarTecnologias(LinearLayout contenedor) {
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        int separadorAlto = Math.max(1, Math.round(getResources().getDisplayMetrics().density));

        for (int i = 0; i < TECNOLOGIAS.length; i++) {
            Tecnologia t = TECNOLOGIAS[i];
            if (i > 0) {
                View separador = new View(requireContext());
                separador.setBackgroundColor(color(R.color.surface_container));
                contenedor.addView(separador, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, separadorAlto));
            }

            View fila = inflater.inflate(R.layout.item_tech_row, contenedor, false);

            ImageView icono = fila.findViewById(R.id.tech_icono);
            icono.setImageResource(t.icono);
            icono.setBackgroundTintList(ColorStateList.valueOf(color(t.etapa.fondo)));
            icono.setImageTintList(ColorStateList.valueOf(color(t.etapa.color)));

            ((TextView) fila.findViewById(R.id.tech_nombre)).setText(t.nombre);
            ((TextView) fila.findViewById(R.id.tech_descripcion)).setText(t.descripcion);

            TextView etapa = fila.findViewById(R.id.tech_etapa);
            etapa.setText(t.etapa.nombre);
            etapa.setTextColor(color(t.etapa.color));
            etapa.setBackgroundTintList(ColorStateList.valueOf(color(t.etapa.fondo)));

            contenedor.addView(fila);
        }
    }

    private void abrirRepositorio() {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.about_github_url)));
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException ignored) {
            // Sin navegador instalado: no hay nada que abrir
        }
    }

    private int color(@ColorRes int res) {
        return ContextCompat.getColor(requireContext(), res);
    }
}
