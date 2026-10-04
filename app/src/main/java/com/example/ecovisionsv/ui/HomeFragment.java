package com.example.ecovisionsv.ui;

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

import com.example.ecovisionsv.R;
import com.example.ecovisionsv.network.ApiStatusChecker.Estado;
import com.example.ecovisionsv.network.InferenciaRepository;
import com.example.ecovisionsv.model.Deteccion;
import com.example.ecovisionsv.model.RespuestaPrediccion;
import com.example.ecovisionsv.utils.NetworkChecker;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class HomeFragment extends Fragment implements MainActivity.ApiStatusObserver {

    private ActivityResultLauncher<String[]> permissionsLauncher;
    private ActivityResultLauncher<Intent> scannerLauncher;
    private ActivityResultLauncher<PickVisualMediaRequest> pickImageLauncher;

    private View actionScan;
    private View actionUpload;
    private View progressInferencia;   // ProgressBar mientras se llama a la API
    private View textoSinConexion;
    private InferenciaRepository repo;

    // estado actual de API, indicado desde MainActivity
    private Estado estadoApi = Estado.CONECTANDO;

    public HomeFragment() {}

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        repo = new InferenciaRepository(requireContext());

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
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        actionScan = view.findViewById(R.id.action_scan);
        actionUpload = view.findViewById(R.id.action_upload);
        progressInferencia = view.findViewById(R.id.progress_inferencia);
        textoSinConexion = view.findViewById(R.id.texto_sin_conexion);

        // Botón "Escanear Residuo", abre ScannerActivity
        actionScan.setOnClickListener(v -> solicitarPermisosYAbrirEscaner());
        // Botón "Subir Imagen", Photo Picker nativo
        actionUpload.setOnClickListener(v -> abrirSelectorDeImagen());

        // aplicar estado inicial de API
        aplicarEstadoApi(estadoApi);
    }

    @Override
    public void onApiEstadoCambiado(Estado estado) {
        estadoApi = estado;
        // La vista puede no estar lista todavía si el fragmento acaba de crearse
        if (getView() != null) aplicarEstadoApi(estado);
    }

    /**
     * Habilita/deshabilita los botones de acción según el estado de la API.
     */
    private void aplicarEstadoApi(Estado estado) {
        boolean disponible = (estado == Estado.CONECTADO);

        actionScan.setEnabled(disponible);
        actionUpload.setEnabled(disponible);
        actionScan.setAlpha(disponible ? 1f : 0.45f);
        actionUpload.setAlpha(disponible ? 1f : 0.45f);

        if (textoSinConexion != null) {
            textoSinConexion.setVisibility(disponible ? View.GONE : View.VISIBLE);
        }
    }

    /**
     * valida/pide permisos antes de abrir la cámara.
     */
    private void solicitarPermisosYAbrirEscaner() {
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


    /**
     * recibe URI, muestra progreso, llama al repositorio
     * y cuando el servidor responde navega a ResultadoActivity.
     */
    private void procesarImagenUri(Uri uri) {
        if (!NetworkChecker.isConnected(requireContext())) {
            Toast.makeText(requireContext(),
                    R.string.error_sin_red, Toast.LENGTH_LONG).show();
            return;
        }

        mostrarCargando(true);

        repo.inferir(uri, new InferenciaRepository.Callback() {
            @Override
            public void onExito(RespuestaPrediccion respuesta,
                                List<Deteccion> filtradas) {
                mostrarCargando(false);
                abrirResultado(uri, respuesta, filtradas);
            }

            @Override
            public void onError(String mensaje) {
                mostrarCargando(false);
                Toast.makeText(requireContext(), mensaje, Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * Navega a ResultadoActivity pasando la URI de la imagen y
     * el JSON completo de la respuesta (para reconstruir los datos).
     */
    private void abrirResultado(Uri imageUri,
                                RespuestaPrediccion respuesta,
                                List<Deteccion> filtradas) {
        Intent intent = new Intent(requireContext(), ResultadoActivity.class);
        intent.putExtra(ResultadoActivity.EXTRA_IMAGE_URI,
                imageUri.toString());
        intent.putExtra(ResultadoActivity.EXTRA_RESPUESTA_JSON,
                new Gson().toJson(respuesta));
        startActivity(intent);
    }

    private void mostrarCargando(boolean cargando) {
        if (progressInferencia == null) return;
        progressInferencia.setVisibility(cargando ? View.VISIBLE : View.GONE);
        actionScan.setEnabled(!cargando);
        actionUpload.setEnabled(!cargando);
    }
}