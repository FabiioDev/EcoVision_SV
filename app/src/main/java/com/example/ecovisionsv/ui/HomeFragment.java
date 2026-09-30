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
import com.example.ecovisionsv.model.Deteccion;
import com.example.ecovisionsv.model.RespuestaPrediccion;
import com.example.ecovisionsv.network.InferenciaRepository;
import com.example.ecovisionsv.utils.NetworkChecker;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class HomeFragment extends Fragment {

    private ActivityResultLauncher<String[]>              permissionsLauncher;
    private ActivityResultLauncher<Intent>                scannerLauncher;
    private ActivityResultLauncher<PickVisualMediaRequest> pickImageLauncher;

    private View progressInferencia;   // ProgressBar mientras se llama a la API
    private InferenciaRepository repo;

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
                uri -> {
                    if (uri != null) procesarImagenUri(uri);
                });
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        progressInferencia = view.findViewById(R.id.progress_inferencia);

        // Botón "Escanear Residuo", abre ScannerActivity
        view.findViewById(R.id.action_scan)
                .setOnClickListener(v -> solicitarPermisosYAbrirEscaner());

        // Botón "Subir Imagen", Photo Picker nativo
        view.findViewById(R.id.action_upload)
                .setOnClickListener(v -> abrirSelectorDeImagen());
    }

    /** valida/pide permisos antes de abrir la cámara. */
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

    private void abrirSelectorDeImagen() {
        pickImageLauncher.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build());
    }

    private void onScannerResult(ActivityResult result) {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) return;

        String uriString = result.getData().getStringExtra(ScannerActivity.EXTRA_IMAGE_URI);
        if (uriString == null) return;

        procesarImagenUri(Uri.parse(uriString));
    }


    /**
     * recibe una URI de imagen (cámara o galería),
     * la redimensiona y llama al servidor de inferencia.
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
                // TODO Fase 2: navegar a ResultadoActivity con los datos
                // Por ahora mostramos un Toast con el resumen
                String resumen = filtradas.isEmpty()
                        ? getString(R.string.inferencia_sin_detecciones)
                        : filtradas.get(0).categoria
                          + " (" + Math.round(filtradas.get(0).confianza * 100) + "%)";
                Toast.makeText(requireContext(),
                        getString(R.string.inferencia_ok, resumen),
                        Toast.LENGTH_LONG).show();
            }

            @Override
            public void onError(String mensaje) {
                mostrarCargando(false);
                Toast.makeText(requireContext(), mensaje, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void mostrarCargando(boolean cargando) {
        if (progressInferencia == null) return;
        progressInferencia.setVisibility(cargando ? View.VISIBLE : View.GONE);
        // Deshabilitar botones mientras se procesa
        View root = getView();
        if (root != null) {
            root.findViewById(R.id.action_scan).setEnabled(!cargando);
            root.findViewById(R.id.action_upload).setEnabled(!cargando);
        }
    }
}