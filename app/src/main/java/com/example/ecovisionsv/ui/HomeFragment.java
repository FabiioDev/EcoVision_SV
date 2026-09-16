package com.example.ecovisionsv.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.example.ecovisionsv.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class HomeFragment extends Fragment {

    private ActivityResultLauncher<String[]> permissionsLauncher;
    private ActivityResultLauncher<Intent> scannerLauncher;

    public HomeFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Los ActivityResultLauncher deben registrarse antes de que el Fragment
        // llegue a STARTED, por eso se hace aquí en onCreate.
        permissionsLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                this::onPermissionsResult);

        scannerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                this::onScannerResult);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        view.findViewById(R.id.action_scan).setOnClickListener(v -> solicitarPermisosYAbrirEscaner());

        // TODO: action_upload puede reutilizar el mismo flujo de permisos y abrir
        // directamente el selector de imágenes dentro de ScannerActivity.
    }

    /** Punto de entrada del botón "Escanear Residuo": valida/pide permisos antes de abrir la cámara. */
    private void solicitarPermisosYAbrirEscaner() {
        String[] permisosFaltantes = permisosPendientes();
        if (permisosFaltantes.length == 0) {
            abrirScannerActivity();
        } else {
            permissionsLauncher.launch(permisosFaltantes);
        }
    }

    /** Determina qué permisos de cámara/almacenamiento hacen falta según la versión de Android. */
    private String[] permisosPendientes() {
        List<String> requeridos = new ArrayList<>();
        requeridos.add(Manifest.permission.CAMERA);

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            // Android 13+: permiso granular para leer imágenes (subir/galería)
            requeridos.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        // Android 10 a 12 (API 29-32): el guardado vía MediaStore/RELATIVE_PATH
        // no necesita permiso explícito.

        List<String> pendientes = new ArrayList<>();
        for (String permiso : requeridos) {
            if (ContextCompat.checkSelfPermission(requireContext(), permiso)
                    != PackageManager.PERMISSION_GRANTED) {
                pendientes.add(permiso);
            }
        }
        return pendientes.toArray(new String[0]);
    }

    private void onPermissionsResult(Map<String, Boolean> resultados) {
        boolean todosConcedidos = true;
        for (Boolean concedido : resultados.values()) {
            todosConcedidos &= Boolean.TRUE.equals(concedido);
        }

        if (todosConcedidos) {
            abrirScannerActivity();
        } else {
            Toast.makeText(requireContext(), R.string.permission_denied, Toast.LENGTH_LONG).show();
        }
    }

    private void abrirScannerActivity() {
        Intent intent = new Intent(requireContext(), ScannerActivity.class);
        scannerLauncher.launch(intent);
    }

    private void onScannerResult(ActivityResult result) {
        if (result.getResultCode() != android.app.Activity.RESULT_OK || result.getData() == null) {
            return;
        }

        String uriString = result.getData().getStringExtra(ScannerActivity.EXTRA_IMAGE_URI);
        if (uriString == null) return;

        Uri imagenUri = Uri.parse(uriString);

        // La imagen ya está guardada en el dispositivo (Pictures/EcoVisionSV);
        // aquí solo se maneja la RUTA (Uri), nunca el archivo en sí.
        // TODO: pasar 'imagenUri' al Clasificador / pantalla de resultados.
    }

}