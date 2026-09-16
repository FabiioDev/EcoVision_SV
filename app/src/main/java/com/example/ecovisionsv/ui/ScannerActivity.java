package com.example.ecovisionsv.ui;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.ecovisionsv.R;
import com.google.common.util.concurrent.ListenableFuture;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ScannerActivity extends AppCompatActivity {

    public static final String EXTRA_IMAGE_URI = "com.example.ecovisionsv.EXTRA_IMAGE_URI";

    private static final String CARPETA_DESTINO = "EcoVisionSV";

    private PreviewView previewView;
    private View buttonCapture;
    private ImageButton buttonFlash;
    private ImageButton buttonGallery;
    private ImageButton buttonClose;
    private ProgressBar progressSaving;

    private ImageCapture imageCapture;
    private Camera camera;
    private ExecutorService cameraExecutor;
    private boolean flashEnabled = false;

    private ActivityResultLauncher<PickVisualMediaRequest> pickImageLauncher;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_scanner);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        bindViews();
        setupGalleryPicker();
        setupClickListeners();

        cameraExecutor = Executors.newSingleThreadExecutor();
        startCamera();
    }

    private void bindViews() {
        previewView = findViewById(R.id.preview_view);
        buttonCapture = findViewById(R.id.button_capture);
        buttonFlash = findViewById(R.id.button_flash);
        buttonGallery = findViewById(R.id.button_gallery);
        buttonClose = findViewById(R.id.button_close);
        progressSaving = findViewById(R.id.progress_saving);
    }

    /** Lanzador para seleccionar una imagen existente del dispositivo (no requiere permiso runtime con Photo Picker). */
    private void setupGalleryPicker() {
        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.PickVisualMedia(),
                uri -> {
                    if (uri != null) {
                        devolverResultado(uri);
                    }
                });
    }

    private void setupClickListeners() {
        buttonClose.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });

        buttonCapture.setOnClickListener(v -> takePhoto());

        buttonFlash.setOnClickListener(v -> toggleFlash());

        buttonGallery.setOnClickListener(v -> pickImageLauncher.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build()));
    }

    private void toggleFlash() {
        if (flashEnabled) {
            imageCapture.setFlashMode(ImageCapture.FLASH_MODE_OFF);
        } else {
            imageCapture.setFlashMode(ImageCapture.FLASH_MODE_ON);
        }
        flashEnabled = !flashEnabled;
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .setFlashMode(ImageCapture.FLASH_MODE_OFF)
                        .build();

                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                cameraProvider.unbindAll();
                camera = cameraProvider.bindToLifecycle(
                        this, cameraSelector, preview, imageCapture);

            } catch (ExecutionException | InterruptedException e) {
                Toast.makeText(this, R.string.error_camera_init, Toast.LENGTH_SHORT).show();
                finish();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void takePhoto() {
        if (imageCapture == null) return;

        setCapturando(true);

        String nombreArchivo = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                .format(System.currentTimeMillis());

        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, "ECOVISION_" + nombreArchivo);
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // API 29+: se guarda dentro de Pictures/EcoVisionSV vía scoped storage
            contentValues.put(MediaStore.Images.Media.RELATIVE_PATH,
                    "Pictures/" + CARPETA_DESTINO);
        }

        ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions
                .Builder(getContentResolver(), MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                .build();

        imageCapture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        Uri savedUri = outputFileResults.getSavedUri();
                        setCapturando(false);
                        if (savedUri != null) {
                            devolverResultado(savedUri);
                        } else {
                            Toast.makeText(ScannerActivity.this, R.string.error_saving_image, Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        setCapturando(false);
                        Toast.makeText(ScannerActivity.this, R.string.error_saving_image, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void setCapturando(boolean capturando) {
        progressSaving.setVisibility(capturando ? View.VISIBLE : View.GONE);
        buttonCapture.setEnabled(!capturando);
        buttonGallery.setEnabled(!capturando);
    }

    /** Devuelve la ruta (URI) de la imagen a quien lanzó esta Activity; la imagen NUNCA se guarda dentro del almacenamiento interno de la app. */
    private void devolverResultado(Uri imageUri) {
        Intent resultIntent = new Intent();
        resultIntent.putExtra(EXTRA_IMAGE_URI, imageUri.toString());
        setResult(RESULT_OK, resultIntent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
    }
}