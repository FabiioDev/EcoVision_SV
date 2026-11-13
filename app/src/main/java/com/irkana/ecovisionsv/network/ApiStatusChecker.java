package com.irkana.ecovisionsv.network;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.irkana.ecovisionsv.api.EcoVisionApi;
import com.irkana.ecovisionsv.model.RespuestaHealth;
import com.irkana.ecovisionsv.utils.NetworkChecker;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Comprueba periódicamente GET /api/v1/health del backend y notifica al observador con
 * uno de los tres estados posibles.
 * Estados:
 * DESCONECTADO   → sin red, o el backend/modelo falló varias veces seguidas
 * CONECTANDO     → hay red pero aún no hay respuesta, o el modelo está despertando
 *                  (inferencia="despertando": arranque en frío; se consulta cada 5 s)
 * CONECTADO      → backend ok e inferencia="lista"
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

    private static final long INTERVALO_MS = 15_000L; // cada 15 s

    private static final long INTERVALO_DESCONECTADO_MS = 60_000L;

    /** Con el modelo despertando se consulta más seguido para pasar pronto a CONECTADO. */
    private static final long INTERVALO_DESPERTANDO_MS = 5_000L;

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

        try {
            EcoVisionApi api = RetrofitClient.getApi();
            if (api == null) {
                registrarFallo();
                return;
            }
            api.health().enqueue(new Callback<RespuestaHealth>() {
                @Override
                public void onResponse(Call<RespuestaHealth> call,
                                       Response<RespuestaHealth> response) {
                    if (!activo) return;

                    RespuestaHealth.Lectura lectura = response.isSuccessful() && response.body() != null
                            ? response.body().leer() : RespuestaHealth.Lectura.FALLO;
                    switch (lectura) {
                        case LISTO:
                            intentosFallidos = 0;            // servicio respondió → reset
                            notificar(Estado.CONECTADO);
                            programarSiguiente(INTERVALO_MS);
                            break;
                        case DESPERTANDO:
                            // El backend responde y despierta el modelo: no es un fallo
                            intentosFallidos = 0;
                            notificar(Estado.CONECTANDO);
                            programarSiguiente(INTERVALO_DESPERTANDO_MS);
                            break;
                        default:
                            registrarFallo();
                    }
                }

                @Override
                public void onFailure(Call<RespuestaHealth> call, Throwable t) {
                    if (!activo) return;
                    registrarFallo();
                }
            });
        } catch (Exception e) {
            registrarFallo();
        }
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
