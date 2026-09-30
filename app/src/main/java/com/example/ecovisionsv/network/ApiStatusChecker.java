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
 *  DESCONECTADO   → sin red en el dispositivo
 *  CONECTANDO     → hay red pero el /health aún no respondió OK
 *  CONECTADO      → /health devolvió {"status":"ok"}
 * Uso:
 *   checker.iniciar(listener);
 *   checker.detener();   // en onPause / onDestroy
 */
public class ApiStatusChecker {

    public enum Estado { DESCONECTADO, CONECTANDO, CONECTADO }

    public interface Listener {
        void onEstadoCambiado(Estado estado);
    }

    private static final long INTERVALO_MS = 30_000L; // cada 30 s

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Listener listener;
    private boolean activo = false;

    public ApiStatusChecker(Context context) {
        this.context = context.getApplicationContext();
    }

    /** Inicia las comprobaciones periódicas e informa inmediatamente el estado actual. */
    public void iniciar(Listener listener) {
        this.listener = listener;
        activo = true;
        verificar();
    }

    /** Detiene las comprobaciones periódicas. Llamar en onPause / onDestroy. */
    public void detener() {
        activo = false;
        handler.removeCallbacksAndMessages(null);
    }

    private void verificar() {
        if (!activo) return;

        if (!NetworkChecker.isConnected(context)) {
            notificar(Estado.DESCONECTADO);
            programarSiguiente();
            return;
        }

        // Hay red: emitir CONECTANDO mientras esperamos la respuesta
        notificar(Estado.CONECTANDO);

        RetrofitClient.getApi().health().enqueue(new Callback<RespuestaHealth>() {
            @Override
            public void onResponse(Call<RespuestaHealth> call,
                                   Response<RespuestaHealth> response) {
                if (response.isSuccessful()
                        && response.body() != null
                        && response.body().isOk()) {
                    notificar(Estado.CONECTADO);
                } else {
                    notificar(Estado.CONECTANDO);
                }
                programarSiguiente();
            }

            @Override
            public void onFailure(Call<RespuestaHealth> call, Throwable t) {
                notificar(Estado.CONECTANDO);
                programarSiguiente();
            }
        });
    }

    private void notificar(Estado estado) {
        if (listener != null) listener.onEstadoCambiado(estado);
    }

    private void programarSiguiente() {
        if (activo) handler.postDelayed(this::verificar, INTERVALO_MS);
    }
}
