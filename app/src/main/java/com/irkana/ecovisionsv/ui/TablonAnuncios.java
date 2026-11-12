package com.irkana.ecovisionsv.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.Layout;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.irkana.ecovisionsv.R;
import com.irkana.ecovisionsv.utils.EnlacesHelper;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tablón de anuncios de Inicio (tarjeta_anuncios en fragment_home.xml).
 *
 * Recibe la lista ya resuelta por AppConfig.getAnnouncements() (vacía si
 * announcements_enabled=false o no hay texto):
 *  - sin anuncios, la tarjeta se oculta; no hay botón para cerrarla;
 *  - un anuncio por página, deslizando en horizontal; con varios, contador
 *    "ANUNCIO 1 DE 3" y puntos; con uno, "ANUNCIOS" sin puntos;
 *  - cada página es pulsable ("Leer más ›" si el texto no cabe, "Abrir ›" si cabe) y abre
 *    una hoja inferior con el texto completo y Anterior / Siguiente;
 *  - solo los enlaces https:// se pueden pulsar.
 */
final class TablonAnuncios {

    private final Context context;
    private final View tarjeta;
    private final RecyclerView carrusel;
    private final TextView contador;
    private final LinearLayout puntos;
    private final Adapter adapter = new Adapter();

    private List<String> anuncios = Collections.emptyList();
    private int actual = 0;

    TablonAnuncios(@NonNull View raiz) {
        context = raiz.getContext();
        tarjeta = raiz.findViewById(R.id.tarjeta_anuncios);
        carrusel = raiz.findViewById(R.id.carrusel_anuncios);
        contador = raiz.findViewById(R.id.texto_contador_anuncios);
        puntos = raiz.findViewById(R.id.puntos_anuncios);

        carrusel.setLayoutManager(
                new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false));
        carrusel.setAdapter(adapter);
        new PagerSnapHelper().attachToRecyclerView(carrusel);
        carrusel.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView rv, int estado) {
                if (estado != RecyclerView.SCROLL_STATE_IDLE) return;
                LinearLayoutManager lm = (LinearLayoutManager) rv.getLayoutManager();
                if (lm == null) return;
                int pos = lm.findFirstCompletelyVisibleItemPosition();
                if (pos != RecyclerView.NO_POSITION && pos != actual) {
                    actual = pos;
                    actualizarIndicadores();
                }
            }
        });
    }

    /** Muestra los anuncios (ya filtrados por la config); lista vacía = tarjeta oculta. */
    void mostrar(@NonNull List<String> nuevos) {
        if (nuevos.equals(anuncios)) return;
        anuncios = new ArrayList<>(nuevos);
        tarjeta.setVisibility(anuncios.isEmpty() ? View.GONE : View.VISIBLE);
        if (actual >= anuncios.size()) actual = 0;
        adapter.notifyDataSetChanged();
        if (!anuncios.isEmpty()) carrusel.scrollToPosition(actual);
        actualizarIndicadores();
    }

    private void actualizarIndicadores() {
        int total = anuncios.size();
        contador.setText(total > 1
                ? context.getString(R.string.anuncios_contador, actual + 1, total)
                : context.getString(R.string.anuncios_titulo));

        puntos.removeAllViews();
        puntos.setVisibility(total > 1 ? View.VISIBLE : View.GONE);
        if (total < 2) return;
        float density = context.getResources().getDisplayMetrics().density;
        int alto = Math.round(6 * density);
        int anchoActivo = Math.round(18 * density);
        int margen = Math.round(3 * density);
        for (int i = 0; i < total; i++) {
            boolean activo = i == actual;
            View punto = new View(context);
            LinearLayout.LayoutParams lp =
                    new LinearLayout.LayoutParams(activo ? anchoActivo : alto, alto);
            lp.setMargins(margen, 0, margen, 0);
            punto.setLayoutParams(lp);
            GradientDrawable forma = new GradientDrawable();
            forma.setCornerRadius(alto / 2f);
            forma.setColor(context.getColor(activo ? R.color.primary : R.color.dot_inactivo));
            punto.setBackground(forma);
            puntos.addView(punto);
        }
    }

    // ------------------------------------------------------------------ //
    // Hoja inferior con el texto completo
    // ------------------------------------------------------------------ //

    private void abrirHoja(int posicion) {
        if (posicion < 0 || posicion >= anuncios.size()) return;
        BottomSheetDialog hoja = new BottomSheetDialog(context);
        View vista = LayoutInflater.from(context).inflate(R.layout.sheet_anuncio, null, false);
        hoja.setContentView(vista);

        TextView titulo = vista.findViewById(R.id.titulo_hoja_anuncio);
        TextView texto = vista.findViewById(R.id.texto_hoja_anuncio);
        View anterior = vista.findViewById(R.id.boton_anuncio_anterior);
        View siguiente = vista.findViewById(R.id.boton_anuncio_siguiente);
        View filaNavegacion = vista.findViewById(R.id.fila_navegacion_anuncio);
        texto.setMovementMethod(LinkMovementMethod.getInstance());

        int[] abierto = {posicion};
        Runnable pintar = () -> {
            int i = abierto[0], total = anuncios.size();
            titulo.setText(total > 1
                    ? context.getString(R.string.anuncio_titulo_hoja_n, i + 1, total)
                    : context.getString(R.string.anuncio_titulo_hoja));
            texto.setText(EnlacesHelper.conEnlaces(anuncios.get(i)));
            filaNavegacion.setVisibility(total > 1 ? View.VISIBLE : View.GONE);
            anterior.setEnabled(i > 0);
            siguiente.setEnabled(i < total - 1);
            // El estilo fija el color del texto: se atenúa a mano el botón deshabilitado
            anterior.setAlpha(anterior.isEnabled() ? 1f : 0.38f);
            siguiente.setAlpha(siguiente.isEnabled() ? 1f : 0.38f);
        };
        anterior.setOnClickListener(v -> {
            if (abierto[0] > 0) { abierto[0]--; pintar.run(); }
        });
        siguiente.setOnClickListener(v -> {
            if (abierto[0] < anuncios.size() - 1) { abierto[0]++; pintar.run(); }
        });
        vista.findViewById(R.id.boton_anuncio_cerrar).setOnClickListener(v -> hoja.dismiss());

        // Al cerrar, el tablón queda en el anuncio que se estaba leyendo
        hoja.setOnDismissListener(d -> {
            if (abierto[0] < anuncios.size() && abierto[0] != actual) {
                actual = abierto[0];
                carrusel.scrollToPosition(actual);
                actualizarIndicadores();
            }
        });
        pintar.run();
        hoja.show();
    }

    // ------------------------------------------------------------------ //
    // Adapter del carrusel
    // ------------------------------------------------------------------ //

    private final class Adapter extends RecyclerView.Adapter<Adapter.VH> {

        final class VH extends RecyclerView.ViewHolder {
            final TextView texto;
            final TextView mas;

            VH(View v) {
                super(v);
                texto = v.findViewById(R.id.texto_anuncio);
                mas = v.findViewById(R.id.texto_anuncio_mas);
                EnlacesHelper.enlacesPulsables(texto);
                v.setOnClickListener(x -> abrirHoja(getAdapterPosition()));
            }
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_anuncio, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            h.texto.setText(EnlacesHelper.conEnlaces(anuncios.get(position)));
            h.itemView.setContentDescription(context.getString(
                    R.string.cd_anuncio, position + 1, anuncios.size()));
            h.mas.setText(R.string.anuncio_abrir);
            // Tras medir el texto: "Leer más" si se cortó con "…"
            h.texto.post(() -> {
                Layout l = h.texto.getLayout();
                int ultima = l != null ? l.getLineCount() - 1 : -1;
                boolean cortado = ultima >= 0 && (l.getEllipsisCount(ultima) > 0
                        || l.getLineEnd(ultima) < h.texto.getText().length());
                h.mas.setText(cortado ? R.string.anuncio_leer_mas : R.string.anuncio_abrir);
            });
        }

        @Override
        public int getItemCount() {
            return anuncios.size();
        }
    }
}
