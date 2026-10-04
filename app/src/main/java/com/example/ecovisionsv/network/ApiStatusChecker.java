package com.example.ecovisionsv.network;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.example.ecovisionsv.model.RespuestaHealth;
import com.example.ecovisionsv.utils.NetworkChecker;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Comprueba periódicamente el estado del servidor EcoVision
 * y notifica al observador con uno de los tres estados posibles.
 * Estados:
 * DESCONECTADO   → sin red en el dispositivo
 * CONECTANDO     → hay red pero el /health aún no respondió OK
 * CONECTADO      → /health devolvió {"status":"ok"}
 * Uso:
 * checker.iniciar(listener);
 * checker.detener();   // en onPause / onDestroy
 */
public class ApiStatusChecker {

    public enum Estado {DESCONECTADO, CONECTANDO, CONECTADO}

    public interface Listener {
        void onEstadoCambiado(Estado estado);
    }

    private static final int MAX_REINTENTOS = 3;

    private static final long INTERVALO_MS = 15_000L; // cada 30 s

    private static final long INTERVALO_DESCONECTADO_MS = 60_000L;

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private Listener listener;
    private boolean activo = false;
    private int intentosFallidos = 0;
    private Estado estadoActual = null;

    public ApiStatusChecker(Context context) {
        this.context = context.getApplicationContext();
    }

    public Estado getEstadoActual() {
        return estadoActual;
    }

    /**
     * Inicia las comprobaciones periódicas e informa inmediatamente el estado actual.
     */
    public void iniciar(Listener listener) {
        this.listener = listener;
        activo = true;
        intentosFallidos = 0;
        verificar();
    }

    /**
     * Detiene las comprobaciones periódicas. Llamar en onPause / onDestroy.
     */
    public void detener() {
        activo = false;
        handler.removeCallbacksAndMessages(null);
    }

    private void verificar() {
        if (!activo) return;

        if (!NetworkChecker.isConnected(context)) {
            intentosFallidos = 0;
            notificar(Estado.DESCONECTADO);
            programarSiguiente(INTERVALO_MS);
            return;
        }

        // Hay red: emitir CONECTANDO mientras esperamos la respuesta
        if (intentosFallidos < MAX_REINTENTOS) {
            notificar(Estado.CONECTANDO);
        }

        RetrofitClient.getApi().health().enqueue(new Callback<RespuestaHealth>() {
            public void onResponse(Call<RespuestaHealth> call,
                                   Response<RespuestaHealth> response) {
                if (!activo) return;

                if (response.isSuccessful()
                        && response.body() != null
                        && response.body().isOk()) {
                    intentosFallidos = 0;            // servicio respondió → reset
                    notificar(Estado.CONECTADO);
                    programarSiguiente(INTERVALO_MS);
                } else {
                    registrarFallo();
                }
            }

            @Override
            public void onFailure(Call<RespuestaHealth> call, Throwable t) {
                if (!activo) return;
                registrarFallo();
            }
        });
    }

    private void registrarFallo() {
        intentosFallidos++;
        if (intentosFallidos >= MAX_REINTENTOS) {
            notificar(Estado.DESCONECTADO);
            // Sigue reintentando en background con intervalo más largo
            programarSiguiente(INTERVALO_DESCONECTADO_MS);
        } else {
            // Aún dentro del rango de reintentos → CONECTANDO
            notificar(Estado.CONECTANDO);
            programarSiguiente(INTERVALO_MS);
        }
    }

    private void notificar(Estado estado) {
        // Solo notifica si el estado realmente cambió para evitar redraws innecesarios
        if (estado != estadoActual) {
            estadoActual = estado;
            if (listener != null) listener.onEstadoCambiado(estado);
        }
    }

    private void programarSiguiente(long delayMs) {
        if (activo) handler.postDelayed(this::verificar, delayMs);
    }
}
