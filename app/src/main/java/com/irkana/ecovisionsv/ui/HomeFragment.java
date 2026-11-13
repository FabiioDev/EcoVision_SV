package com.irkana.ecovisionsv.ui;

import android.widget.TextView;
import android.widget.ImageView;
import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.irkana.ecovisionsv.config.AppConfig;
import com.irkana.ecovisionsv.config.ConfigRepository;
import com.irkana.ecovisionsv.R;
import com.irkana.ecovisionsv.network.ApiStatusChecker.Estado;
import com.irkana.ecovisionsv.utils.NetworkChecker;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class HomeFragment extends Fragment
        implements MainActivity.ApiStatusObserver, MainActivity.ConfigObserver {

    private ActivityResultLauncher<String[]> permissionsLauncher;
    private ActivityResultLauncher<Intent> scannerLauncher;
    private TablonAnuncios tablonAnuncios;
    private ActivityResultLauncher<PickVisualMediaRequest> pickImageLauncher;
    private ActivityResultLauncher<Intent> resultadoLauncher;

    private View actionScan;
    private View actionUpload;
    private View textoSinConexion;

    // estado actual de API, indicado desde MainActivity
    private Estado estadoApi = Estado.CONECTANDO;

    public HomeFragment() {}

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // permisos de camara/almacenamiento
        permissionsLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                this::onPermissionsResult);

        // Resultado de ScannerActivity (foto tomada o galería seleccionada en la cámara)
        scannerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                this::onScannerResult);

        // Photo Picker nativo (para "Subir Imagen" del HomeFragment)
        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.PickVisualMedia(),
                uri -> {if (uri != null) procesarImagenUri(uri);});

        // Resultado de ResultadoActivity ("Nueva detección" vuelve a abrir el escáner)
        resultadoLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                this::onResultadoCerrado);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tablonAnuncios = new TablonAnuncios(view);
        // "Reintentar" de la tarjeta "Funciones no disponibles": vuelve a pedir la config
        View reintentar = view.findViewById(R.id.boton_reintentar_config);
        reintentar.setOnClickListener(v -> {
            reintentar.setEnabled(false);
            ConfigRepository.getInstance(requireContext()).refrescarAhora(() -> {
                if (getView() == null) return;
                reintentar.setEnabled(true);
                aplicarConfig(ConfigRepository.getInstance(requireContext()).get());
            });
        });
        actionScan = view.findViewById(R.id.action_scan);
        actionUpload = view.findViewById(R.id.action_upload);
        textoSinConexion = view.findViewById(R.id.texto_sin_conexion);

        // Botón "Escanear Residuo", abre ScannerActivity
        actionScan.setOnClickListener(v -> solicitarPermisosYAbrirEscaner());
        // Botón "Subir Imagen", Photo Picker nativo
        actionUpload.setOnClickListener(v -> abrirSelectorDeImagen());

        // Si MainActivity ya conoce el estado actual de la API, sincronizar
        if (getActivity() instanceof MainActivity) {
            Estado actual = ((MainActivity) getActivity()).getEstadoApiActual();
            if (actual != null) {
                estadoApi = actual;
            }
        }

        // aplicar estado inicial de API
        aplicarEstadoApi(estadoApi);
        aplicarConfig(ConfigRepository.getInstance(requireContext()).get());
    }

    @Override
    public void onConfigCambiada(AppConfig config) {
        if (getView() != null) aplicarConfig(config);
    }

    /**
     * Muestra u oculta lo que depende de la config. Una función desactivada se oculta
     * (decisión del autor), no se deshabilita.
     */
    private void aplicarConfig(AppConfig config) {
        boolean camara = config.isCameraEnabled();
        boolean galeria = config.isGalleryEnabled();
        actionScan.setVisibility(camara ? View.VISIBLE : View.GONE);
        actionUpload.setVisibility(galeria ? View.VISIBLE : View.GONE);

        // Sin ninguna de las dos: se quita el encabezado y se muestra la tarjeta
        // "Funciones no disponibles" (tablero "TodoApagado")
        View view = requireView();
        boolean ninguna = !camara && !galeria;
        view.findViewById(R.id.encabezado_acciones)
                .setVisibility(ninguna ? View.GONE : View.VISIBLE);
        view.findViewById(R.id.tarjeta_funciones_no_disponibles)
                .setVisibility(ninguna ? View.VISIBLE : View.GONE);

        // tips_enabled=false: solo se oculta el "Tip del día"; los consejos por categoría
        // del resultado se mantienen (decisión del autor)
        view.findViewById(R.id.tarjeta_tip)
                .setVisibility(config.isTipsEnabled() ? View.VISIBLE : View.GONE);

        // Anuncios: lista ya resuelta (vacía si announcements_enabled=false o sin texto)
        tablonAnuncios.mostrar(config.getAnnouncements());

        // Mantenimiento: aviso y botones de detección en gris
        aplicarEstadoApi(estadoApi);
    }

    @Override
    public void onApiEstadoCambiado(Estado estado) {
        estadoApi = estado;
        // La vista puede no estar lista todavía si el fragmento acaba de crearse
        if (getView() != null) aplicarEstadoApi(estado);
    }

    /**
     * Habilita/deshabilita los botones de detección y muestra el aviso de estado según la
     * API y el modo mantenimiento (maintenance_mode). En mantenimiento solo se bloquea la
     * detección: historial, Acerca de y anuncios siguen disponibles. El aviso de
     * mantenimiento tiene prioridad sobre el de "sin conexión".
     */
    private void aplicarEstadoApi(Estado estado) {
        View view = getView();
        if (view == null) return;
        AppConfig config = ConfigRepository.getInstance(requireContext()).get();
        boolean mantenimiento = config.isMaintenanceMode();
        boolean disponible = (estado == Estado.CONECTADO) && !mantenimiento;

        actionScan.setEnabled(disponible);
        actionUpload.setEnabled(disponible);
        actionScan.setAlpha(disponible ? 1f : 0.45f);
        actionUpload.setAlpha(disponible ? 1f : 0.45f);
        ((TextView) view.findViewById(R.id.texto_cta_escanear)).setText(
                mantenimiento ? R.string.mantenimiento_no_disponible : R.string.action_scan_cta);
        ((TextView) view.findViewById(R.id.texto_descripcion_subir)).setText(
                mantenimiento ? R.string.mantenimiento_subir_descripcion
                        : R.string.action_upload_descripcion);

        if (textoSinConexion == null) return;
        textoSinConexion.setVisibility(disponible ? View.GONE : View.VISIBLE);
        ImageView icono = view.findViewById(R.id.icono_aviso_estado);
        TextView titulo = view.findViewById(R.id.titulo_aviso_estado);
        TextView texto = view.findViewById(R.id.texto_aviso_estado);
        if (mantenimiento) {
            textoSinConexion.setBackgroundResource(R.drawable.bg_aviso_mantenimiento);
            icono.setImageResource(R.drawable.ic_build);
            icono.setImageTintList(ContextCompat.getColorStateList(
                    requireContext(), R.color.aviso_mantenimiento_icono));
            titulo.setVisibility(View.VISIBLE);
            texto.setText(config.getMaintenanceMessage());
        } else if (estado == Estado.CONECTANDO) {
            // El modelo puede estar arrancando (arranque en frío): no es falta de conexión
            textoSinConexion.setBackgroundResource(R.drawable.bg_aviso_sin_conexion);
            icono.setImageResource(R.drawable.ic_refresh);
            icono.setImageTintList(ContextCompat.getColorStateList(
                    requireContext(), R.color.status_connecting));
            titulo.setVisibility(View.GONE);
            texto.setText(R.string.mensaje_preparando_deteccion);
        } else {
            textoSinConexion.setBackgroundResource(R.drawable.bg_aviso_sin_conexion);
            icono.setImageResource(R.drawable.ic_wifi_off);
            icono.setImageTintList(ContextCompat.getColorStateList(
                    requireContext(), R.color.status_disconnected));
            titulo.setVisibility(View.GONE);
            texto.setText(R.string.mensaje_sin_conexion);
        }
    }

    /**
     * valida/pide permisos antes de abrir la cámara.
     */
    private void solicitarPermisosYAbrirEscaner() {
        // También se llega desde "Nueva detección" en el resultado
        if (!ConfigRepository.getInstance(requireContext()).get().isCameraEnabled()) return;
        if (enMantenimiento()) return;
        String[] permisosFaltantes = permisosPendientes();
        if (permisosFaltantes.length == 0) {
            abrirScannerActivity();
        } else {
            permissionsLauncher.launch(permisosFaltantes);
        }
    }

    private String[] permisosPendientes() {
        List<String> requeridos = new ArrayList<>();
        requeridos.add(Manifest.permission.CAMERA);
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            requeridos.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        List<String> pendientes = new ArrayList<>();
        for (String p : requeridos) {
            if (ContextCompat.checkSelfPermission(requireContext(), p)
                    != PackageManager.PERMISSION_GRANTED) {
                pendientes.add(p);
            }
        }
        return pendientes.toArray(new String[0]);
    }

    private void onPermissionsResult(Map<String, Boolean> resultados) {
        boolean todos = true;
        for (Boolean v : resultados.values()) todos &= Boolean.TRUE.equals(v);
        if (todos) abrirScannerActivity();
        else Toast.makeText(requireContext(), R.string.permission_denied, Toast.LENGTH_LONG).show();
    }

    private void abrirScannerActivity() {
        scannerLauncher.launch(new Intent(requireContext(), ScannerActivity.class));
    }

    // galeria
    private void abrirSelectorDeImagen() {
        if (!ConfigRepository.getInstance(requireContext()).get().isGalleryEnabled()) return;
        if (enMantenimiento()) return;
        pickImageLauncher.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build());
    }

    // resultado scanner
    private void onScannerResult(ActivityResult result) {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) return;
        String uriString = result.getData().getStringExtra(ScannerActivity.EXTRA_IMAGE_URI);
        if (uriString == null) return;
        procesarImagenUri(Uri.parse(uriString));
    }

    private void onResultadoCerrado(ActivityResult result) {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) return;
        if (result.getData().getBooleanExtra(ResultadoActivity.EXTRA_NUEVA_DETECCION, false)) {
            solicitarPermisosYAbrirEscaner();
        }
    }


    /**
     * Abre ResultadoActivity con la URI: ahí se muestra el skeleton
     * mientras se llama a la API y luego el resultado.
     */
    private void procesarImagenUri(Uri uri) {
        if (enMantenimiento()) return;
        if (!NetworkChecker.isConnected(requireContext())) {
            Toast.makeText(requireContext(),
                    R.string.error_sin_red, Toast.LENGTH_LONG).show();
            return;
        }

        Intent intent = new Intent(requireContext(), ResultadoActivity.class);
        intent.putExtra(ResultadoActivity.EXTRA_IMAGE_URI, uri.toString());
        resultadoLauncher.launch(intent);
    }

    /**
     * true si la detección está en mantenimiento (maintenance_mode). Comprobación extra por
     * si la config cambió con la pantalla abierta: muestra el mensaje y actualiza la vista.
     */
    private boolean enMantenimiento() {
        AppConfig config = ConfigRepository.getInstance(requireContext()).get();
        if (!config.isMaintenanceMode()) return false;
        Toast.makeText(requireContext(), config.getMaintenanceMessage(), Toast.LENGTH_LONG).show();
        aplicarEstadoApi(estadoApi);
        return true;
    }
}