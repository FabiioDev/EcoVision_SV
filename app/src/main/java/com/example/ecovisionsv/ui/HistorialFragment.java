package com.example.ecovisionsv.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.ecovisionsv.R;
import com.example.ecovisionsv.database.DeteccionDao;
import com.example.ecovisionsv.database.DeteccionEntity;
import com.example.ecovisionsv.database.EcoVisionDatabase;
import com.example.ecovisionsv.model.Deteccion;
import com.example.ecovisionsv.model.RespuestaPrediccion;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.Gson;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class HistorialFragment extends Fragment {

    private RecyclerView recyclerView;
    private View estadoVacio;
    private HistorialAdapter adapter;
    private DeteccionDao dao;


    public HistorialFragment() {
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_historial, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerView = view.findViewById(R.id.recycler_historial);
        estadoVacio = view.findViewById(R.id.estado_vacio);

        dao = EcoVisionDatabase.getInstance(requireContext()).deteccionDao();

        adapter = new HistorialAdapter(new ArrayList<>(), this::abrirDetalle, this::confirmarEliminar);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        cargarHistorial();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Recargar al volver de ResultadoActivity por si hay entradas nuevas
        cargarHistorial();
    }

    private void cargarHistorial() {
        EcoVisionDatabase.executor.execute(() -> {
            List<DeteccionEntity> lista = dao.obtenerTodos();
            // El resumen se arma aquí para no parsear el JSON en onBindViewHolder
            List<String> resumenes = new ArrayList<>();
            for (DeteccionEntity e : lista) resumenes.add(resumenCategorias(e));
            if (getActivity() == null) return;
            requireActivity().runOnUiThread(() -> {
                adapter.actualizar(lista, resumenes);
                boolean vacio = lista.isEmpty();
                estadoVacio.setVisibility(vacio ? View.VISIBLE : View.GONE);
                recyclerView.setVisibility(vacio ? View.GONE : View.VISIBLE);
            });
        });
    }

    /**
     * Resume las categorías detectadas, p. ej. "plastico ×2 · metal",
     * ordenadas por la confianza de su mejor detección.
     */
    private static String resumenCategorias(DeteccionEntity e) {
        List<Deteccion> detecciones = null;
        if (e.resultadoJson != null) {
            try {
                RespuestaPrediccion r = new Gson().fromJson(e.resultadoJson, RespuestaPrediccion.class);
                if (r != null) detecciones = r.deteccionesFiltradas(0.45f);
            } catch (RuntimeException ignored) {
                // JSON corrupto: se usa la categoría principal guardada
            }
        }
        if (detecciones == null || detecciones.isEmpty()) {
            return e.categoriaPrincipal != null ? e.categoriaPrincipal : "—";
        }

        Map<String, Integer> conteo = new LinkedHashMap<>();
        for (Deteccion d : detecciones) {
            String cat = d.categoria != null ? d.categoria : "—";
            Integer n = conteo.get(cat);
            conteo.put(cat, n == null ? 1 : n + 1);
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> entry : conteo.entrySet()) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(entry.getKey());
            if (entry.getValue() > 1) sb.append(" ×").append(entry.getValue());
        }
        return sb.toString();
    }

    /**
     * Abre ResultadoActivity reutilizando el JSON guardado en la BD.
     */
    private void abrirDetalle(DeteccionEntity entidad) {
        if (entidad.resultadoJson == null) {
            Toast.makeText(requireContext(),
                    R.string.error_datos_no_disponibles, Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(requireContext(), ResultadoActivity.class);
        intent.putExtra(ResultadoActivity.EXTRA_IMAGE_URI, entidad.imagenUri);
        intent.putExtra(ResultadoActivity.EXTRA_RESPUESTA_JSON, entidad.resultadoJson);
        intent.putExtra(ResultadoActivity.EXTRA_DESDE_HISTORIAL, true);
        startActivity(intent);
    }

    private void confirmarEliminar(DeteccionEntity entidad) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.historial_eliminar_titulo)
                .setMessage(R.string.historial_eliminar_mensaje)
                .setNegativeButton(R.string.historial_eliminar_cancelar, null)
                .setPositiveButton(R.string.historial_eliminar_confirmar, (d, w) -> eliminar(entidad))
                .show();
    }

    /**
     * Borra el registro y la imagen con bboxes (almacenamiento privado de la app).
     * La foto original no se toca: pertenece a la galería del usuario.
     */
    private void eliminar(DeteccionEntity entidad) {
        EcoVisionDatabase.executor.execute(() -> {
            dao.eliminarPorId(entidad.id);
            if (entidad.imagenBboxUri != null) {
                Uri bbox = Uri.parse(entidad.imagenBboxUri);
                if ("file".equals(bbox.getScheme()) && bbox.getPath() != null) {
                    //noinspection ResultOfMethodCallIgnored
                    new File(bbox.getPath()).delete();
                }
            }
            if (getActivity() == null) return;
            requireActivity().runOnUiThread(() -> {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), R.string.historial_eliminado, Toast.LENGTH_SHORT).show();
                cargarHistorial();
            });
        });
    }

    static class HistorialAdapter
            extends RecyclerView.Adapter<HistorialAdapter.VH> {

        interface OnItemClick {
            void onClick(DeteccionEntity e);
        }

        private final List<DeteccionEntity> items;
        private final List<String> resumenes = new ArrayList<>();
        private final OnItemClick listener;
        private final OnItemClick eliminarListener;

        private static final SimpleDateFormat SDF =
                new SimpleDateFormat("dd MMM yyyy  HH:mm", new Locale("es"));

        HistorialAdapter(List<DeteccionEntity> items, OnItemClick listener,
                         OnItemClick eliminarListener) {
            this.items = items;
            this.listener = listener;
            this.eliminarListener = eliminarListener;
        }

        void actualizar(List<DeteccionEntity> nuevos, List<String> nuevosResumenes) {
            items.clear();
            items.addAll(nuevos);
            resumenes.clear();
            resumenes.addAll(nuevosResumenes);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_historial, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            DeteccionEntity e = items.get(pos);

            // Miniatura: intentar con imagen bbox primero, luego la original
            if (!cargarMiniatura(h.miniatura, e.imagenBboxUri)
                    && !cargarMiniatura(h.miniatura, e.imagenUri)) {
                h.miniatura.setImageResource(R.drawable.ic_image);
            }

            // Categorías detectadas + número de detecciones
            h.textoCategoria.setText(resumenes.get(pos));
            h.textoDetecciones.setText(h.itemView.getContext().getResources()
                    .getQuantityString(R.plurals.detecciones_encontradas,
                            e.numDetecciones, e.numDetecciones));

            // Fecha y hora
            h.textoFecha.setText(SDF.format(new Date(e.timestamp)));

            h.itemView.setOnClickListener(v -> listener.onClick(e));
            h.botonEliminar.setOnClickListener(v -> eliminarListener.onClick(e));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        /**
         * La imagen puede haberse borrado de la galería o ser una URI del Photo Picker
         * guardada antes de copiarlas (sin permiso tras reiniciar la app).
         */
        private static boolean cargarMiniatura(ImageView destino, String uri) {
            if (uri == null) return false;
            try {
                destino.setImageURI(null);
                destino.setImageURI(Uri.parse(uri));
                return destino.getDrawable() != null;
            } catch (SecurityException ex) {
                return false;
            }
        }

        static class VH extends RecyclerView.ViewHolder {
            ImageView miniatura;
            TextView textoCategoria;
            TextView textoDetecciones;
            TextView textoFecha;
            ImageView botonEliminar;

            VH(@NonNull View v) {
                super(v);
                miniatura = v.findViewById(R.id.miniatura_historial);
                textoCategoria = v.findViewById(R.id.texto_categoria_historial);
                textoDetecciones = v.findViewById(R.id.texto_detecciones_historial);
                textoFecha = v.findViewById(R.id.texto_fecha_historial);
                botonEliminar = v.findViewById(R.id.boton_eliminar_historial);
            }
        }
    }
}