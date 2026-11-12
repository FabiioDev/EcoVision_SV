package com.example.ecovisionsv.ui;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import androidx.annotation.NonNull;
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
import com.example.ecovisionsv.config.AppConfig;
import com.example.ecovisionsv.config.ConfigRepository;
import com.example.ecovisionsv.network.ApiStatusChecker;
import com.example.ecovisionsv.network.ApiStatusChecker.Estado;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.example.ecovisionsv.ui.HistorialFragment;

import org.checkerframework.common.subtyping.qual.Bottom;

public class MainActivity extends AppCompatActivity
        implements ApiStatusChecker.Listener, ConfigRepository.Listener {

    // Vistas del indicador de estado en el AppBar
    private View statusDot;
    private TextView statusText;
    private ApiStatusChecker statusChecker;
    private BottomNavigationView bottomNav;
    private Estado estadoApi;

    /**
     * Interfaz que los Fragments pueden implementar para recibir
     * cambios de estado de la API
     */
    /** Fragments que dependen de la config: reciben la nueva cuando cambia. */
    public interface ConfigObserver {
        void onConfigCambiada(AppConfig config);
    }

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
        bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setOnItemSelectedListener(item -> {
            Fragment fragment = null;
            int id = item.getItemId();

            if (id == R.id.nav_home) {
                fragment = new HomeFragment();
            } else if (id == R.id.nav_history) {
                fragment = new HistorialFragment();
            } else if (id == R.id.nav_about) {
                fragment = new AboutFragment();
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
        // Config: se aplica la vigente y se pide la remota en segundo plano, sin bloquear
        // (al abrir y al volver a primer plano); si llega una distinta, onConfigCambiada.
        ConfigRepository config = ConfigRepository.getInstance(this);
        aplicarConfig(config.get());
        config.agregarListener(this);
        config.refrescar();
        if (statusChecker != null) {
            try {
                statusChecker.iniciar(this);
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        ConfigRepository.getInstance(this).quitarListener(this);
        if (statusChecker != null) {
            try {
                statusChecker.detener();
            } catch (Exception ignored) {
            }
        }
    }


    // ConfigRepository.Listener
    @Override
    public void onConfigCambiada(@NonNull AppConfig config) {
        aplicarConfig(config);
        Fragment current = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (current instanceof ConfigObserver) {
            ((ConfigObserver) current).onConfigCambiada(config);
        }
    }

    /**
     * Aplica la config a lo que depende de esta Activity: indicador de estado de la API y
     * pestañas. Único lugar donde se decide qué pestañas se ven:
     *  - una pestaña desactivada se quita de la barra; si estaba abierta, se vuelve a Inicio;
     *  - si solo queda Inicio, se oculta la barra.
     */
    private void aplicarConfig(AppConfig config) {
        pintarIndicador();

        // show_api_status=false: solo se oculta el indicador; la comprobación sigue y
        // Inicio mantiene el aviso "sin conexión" y el atenuado de los botones.
        View indicador = findViewById(R.id.api_status_container);
        if (indicador != null) {
            indicador.setVisibility(config.isShowApiStatus() ? View.VISIBLE : View.GONE);
        }

        if (bottomNav == null) return;
        Menu menu = bottomNav.getMenu();
        menu.findItem(R.id.nav_history).setVisible(config.isHistoryEnabled());
        menu.findItem(R.id.nav_about).setVisible(config.isAboutEnabled());

        MenuItem seleccionado = menu.findItem(bottomNav.getSelectedItemId());
        if (seleccionado != null && !seleccionado.isVisible()) {
            bottomNav.setSelectedItemId(R.id.nav_home);
        }

        int visibles = 0;
        for (int i = 0; i < menu.size(); i++) {
            if (menu.getItem(i).isVisible()) visibles++;
        }
        bottomNav.setVisibility(visibles > 1 ? View.VISIBLE : View.GONE);
    }

    // ApiStatusChecker.Listener
    @Override
    public void onEstadoCambiado(Estado estado) {
        estadoApi = estado;
        pintarIndicador();
        propagarAFragment(estado);
    }

    /**
     * Etiqueta de estado de la barra superior. Con maintenance_mode=true muestra
     * "En mantenimiento" (tiene prioridad sobre el estado de la conexión).
     */
    private void pintarIndicador() {
        if (statusDot == null || statusText == null || estadoApi == null) return;

        int colorRes;
        int stringRes;

        if (ConfigRepository.getInstance(this).get().isMaintenanceMode()) {
            statusDot.setBackgroundTintList(
                    ContextCompat.getColorStateList(this, R.color.aviso_mantenimiento_icono));
            statusText.setText(R.string.api_status_mantenimiento);
            return;
        }

        switch (estadoApi) {
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