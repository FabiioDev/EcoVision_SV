package com.example.ecovisionsv.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
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

public class MainActivity extends AppCompatActivity
        implements ApiStatusChecker.Listener{

    // Vistas del indicador de estado en el AppBar
    private View statusDot;
    private TextView statusText;

    private ApiStatusChecker statusChecker;

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
        statusDot  = findViewById(R.id.api_status_dot);
        statusText = findViewById(R.id.api_status_text);
    }

    private void setupBottomNav() {
        com.google.android.material.bottomnavigation.BottomNavigationView bottomNav =
                findViewById(R.id.bottom_nav);

        bottomNav.setOnItemSelectedListener(item -> {
            Fragment fragment = null;
            int id = item.getItemId();

            if (id == R.id.nav_home) {
                fragment = new HomeFragment();
            }
            // Fase 3: nav_history → HistorialFragment
            // Fase 4: nav_profile → ProfileFragment

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

        switch (estado) {
            case CONECTADO:
                statusDot.setBackgroundTintList(
                        ContextCompat.getColorStateList(this, R.color.status_connected));
                statusText.setText(R.string.api_status_connected);
                break;

            case CONECTANDO:
                statusDot.setBackgroundTintList(
                        ContextCompat.getColorStateList(this, R.color.status_connecting));
                statusText.setText(R.string.api_status_connecting);
                break;

            case DESCONECTADO:
            default:
                statusDot.setBackgroundTintList(
                        ContextCompat.getColorStateList(this, R.color.status_disconnected));
                statusText.setText(R.string.api_status_disconnected);
                break;
        }
    }
}