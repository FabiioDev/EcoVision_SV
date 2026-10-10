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
import com.google.gson.Gson;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

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

        adapter = new HistorialAdapter(new ArrayList<>(), this::abrirDetalle);
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
            if (getActivity() == null) return;
            requireActivity().runOnUiThread(() -> {
                adapter.actualizar(lista);
                boolean vacio = lista.isEmpty();
                estadoVacio.setVisibility(vacio ? View.VISIBLE : View.GONE);
                recyclerView.setVisibility(vacio ? View.GONE : View.VISIBLE);
            });
        });
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
        startActivity(intent);
    }

    static class HistorialAdapter
            extends RecyclerView.Adapter<HistorialAdapter.VH> {

        interface OnItemClick {
            void onClick(DeteccionEntity e);
        }

        private final List<DeteccionEntity> items;
        private final OnItemClick listener;

        private static final SimpleDateFormat SDF =
                new SimpleDateFormat("dd MMM yyyy  HH:mm", new Locale("es"));

        HistorialAdapter(List<DeteccionEntity> items, OnItemClick listener) {
            this.items = items;
            this.listener = listener;
        }

        void actualizar(List<DeteccionEntity> nuevos) {
            items.clear();
            items.addAll(nuevos);
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
            String uriMiniatura = e.imagenBboxUri != null
                    ? e.imagenBboxUri : e.imagenUri;
            if (uriMiniatura != null) {
                h.miniatura.setImageURI(Uri.parse(uriMiniatura));
            } else {
                h.miniatura.setImageResource(R.drawable.ic_image);
            }

            // Categoría principal + número de detecciones
            h.textoCategoria.setText(e.categoriaPrincipal != null
                    ? e.categoriaPrincipal : "—");
            h.textoDetecciones.setText(h.itemView.getContext().getResources()
                    .getQuantityString(R.plurals.detecciones_encontradas,
                            e.numDetecciones, e.numDetecciones));

            // Fecha y hora
            h.textoFecha.setText(SDF.format(new Date(e.timestamp)));

            // Confianza
            int pct = Math.round(e.confianzaPrincipal * 100);
            h.textoConfianza.setText(pct + "%");

            h.itemView.setOnClickListener(v -> listener.onClick(e));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            ImageView miniatura;
            TextView textoCategoria;
            TextView textoDetecciones;
            TextView textoFecha;
            TextView textoConfianza;

            VH(@NonNull View v) {
                super(v);
                miniatura = v.findViewById(R.id.miniatura_historial);
                textoCategoria = v.findViewById(R.id.texto_categoria_historial);
                textoDetecciones = v.findViewById(R.id.texto_detecciones_historial);
                textoFecha = v.findViewById(R.id.texto_fecha_historial);
                textoConfianza = v.findViewById(R.id.texto_confianza_historial);
            }
        }
    }
}