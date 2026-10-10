package com.example.ecovisionsv.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.example.ecovisionsv.R;
import com.example.ecovisionsv.network.ApiStatusChecker;
import com.example.ecovisionsv.network.ApiStatusChecker.Estado;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.example.ecovisionsv.ui.HistorialFragment;

import org.checkerframework.common.subtyping.qual.Bottom;

public class MainActivity extends AppCompatActivity
        implements ApiStatusChecker.Listener {

    // Vistas del indicador de estado en el AppBar
    private View statusDot;
    private TextView statusText;
    private ApiStatusChecker statusChecker;

    /**
     * Interfaz que los Fragments pueden implementar para recibir
     * cambios de estado de la API
     */
    public interface ApiStatusObserver {
        void onApiEstadoCambiado(Estado estado);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        bindViews();
        setupBottomNav();

        statusChecker = new ApiStatusChecker(this);

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new HomeFragment())
                    .commit();
        }
    }

    private void bindViews() {
        statusDot = findViewById(R.id.api_status_dot);
        statusText = findViewById(R.id.api_status_text);
    }

    private void setupBottomNav() {
        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setOnItemSelectedListener(item -> {
            Fragment fragment = null;
            int id = item.getItemId();

            if (id == R.id.nav_home) {
                fragment = new HomeFragment();
            } else if (id == R.id.nav_history) {
                fragment = new HistorialFragment();
            }

            if (fragment != null) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, fragment)
                        .commit();
            }
            return fragment != null;
        });
    }

    // Ciclo de vida: iniciar/detener el checker con la Activity
    @Override
    protected void onResume() {
        super.onResume();
        statusChecker.iniciar(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        statusChecker.detener();
    }


    // ApiStatusChecker.Listener
    @Override
    public void onEstadoCambiado(Estado estado) {
        if (statusDot == null || statusText == null) return;

        int colorRes;
        int stringRes;

        switch (estado) {
            case CONECTADO:
                colorRes  = R.color.status_connected;
                stringRes = R.string.api_status_connected;
                break;
            case CONECTANDO:
                colorRes  = R.color.status_connecting;
                stringRes = R.string.api_status_connecting;
                break;
            case DESCONECTADO:
            default:
                colorRes  = R.color.status_disconnected;
                stringRes = R.string.api_status_disconnected;
                break;
        }

        statusDot.setBackgroundTintList(
                ContextCompat.getColorStateList(this, colorRes));
        statusText.setText(stringRes);

        propagarAFragment(estado);
    }

    public Estado getEstadoApiActual() {
        return statusChecker != null ? statusChecker.getEstadoActual() : null;
    }

    /** Propaga el estado al Fragment activo si implementa ApiStatusObserver. */
    private void propagarAFragment(Estado estado) {
        Fragment current = getSupportFragmentManager()
                .findFragmentById(R.id.fragment_container);
        if (current instanceof ApiStatusObserver) {
            ((ApiStatusObserver) current).onApiEstadoCambiado(estado);
        }
    }
}