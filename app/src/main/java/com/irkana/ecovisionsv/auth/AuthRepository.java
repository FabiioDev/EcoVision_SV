package com.irkana.ecovisionsv.auth;

import android.app.Activity;
import android.content.Context;
import android.os.CancellationSignal;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.credentials.ClearCredentialStateRequest;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.ClearCredentialException;
import androidx.credentials.exceptions.GetCredentialCancellationException;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.credentials.exceptions.NoCredentialException;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.AdditionalUserInfo;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GetTokenResult;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.UserInfo;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.irkana.ecovisionsv.R;

import java.util.concurrent.TimeUnit;

/**
 * Único punto de acceso a la sesión del usuario (Firebase Authentication, decisión D1).
 * Proveedores: correo/contraseña y Google (Credential Manager).
 *
 * Las pantallas nunca usan FirebaseAuth directamente. Los resultados llegan en el hilo
 * principal con {@link Resultado}; los errores ya traducidos a {@link AuthErrores.Motivo}.
 *
 * La sesión la guarda Firebase en el teléfono: tras el primer login la app abre sin
 * conexión (offline-first, D5).
 */
public final class AuthRepository {

    private static final String TAG = "AuthRepository";

    /** Espera máxima por el token en el interceptor (nunca en el hilo principal). */
    private static final int TOKEN_TIMEOUT_S = 5;

    public static final String PROVEEDOR_PASSWORD = EmailAuthProvider.PROVIDER_ID;
    public static final String PROVEEDOR_GOOGLE = "google.com";

    public interface Resultado {
        void ok();

        void error(@NonNull AuthErrores.Motivo motivo);
    }

    private static volatile AuthRepository instance;

    private final FirebaseAuth auth;

    private static final long COMPROBACION_MIN_MS = 60_000L;
    private long ultimaComprobacion = 0L;

    private AuthRepository() {
        auth = FirebaseAuth.getInstance();
        // Correos de Firebase (recuperar contraseña) en el idioma del teléfono
        auth.useAppLanguage();
    }

    @NonNull
    public static AuthRepository getInstance() {
        if (instance == null) {
            synchronized (AuthRepository.class) {
                if (instance == null) instance = new AuthRepository();
            }
        }
        return instance;
    }

    // ------------------------------------------------------------------ //
    // Estado de la sesión
    // ------------------------------------------------------------------ //

    @Nullable
    public FirebaseUser usuario() {
        return auth.getCurrentUser();
    }

    public boolean haySesion() {
        return auth.getCurrentUser() != null;
    }

    /** Proveedor con el que entró el usuario (correo o Google), o null. */
    @Nullable
    public String proveedor() {
        FirebaseUser u = auth.getCurrentUser();
        if (u == null) return null;
        for (UserInfo info : u.getProviderData()) {
            String p = info.getProviderId();
            if (PROVEEDOR_GOOGLE.equals(p) || PROVEEDOR_PASSWORD.equals(p)) return p;
        }
        return null;
    }

    /**
     * Comprueba con Firebase que la cuenta sigue existiendo y activa (p. ej. desactivada o
     * borrada desde el panel). Sin conexión no hace nada: la sesión guardada sigue valiendo.
     * {@code siInvalida} se ejecuta en el hilo principal tras cerrar la sesión.
     */
    public void comprobarCuenta(@NonNull Context context, @NonNull Runnable siInvalida) {
        FirebaseUser u = auth.getCurrentUser();
        if (u == null) return;
        // Como mucho una vez por minuto (onResume se llama al volver de cada pantalla)
        long ahora = SystemClock.elapsedRealtime();
        if (ultimaComprobacion != 0 && ahora - ultimaComprobacion < COMPROBACION_MIN_MS) return;
        ultimaComprobacion = ahora;
        u.reload().addOnFailureListener(e -> {
            if (e instanceof FirebaseAuthInvalidUserException) {
                Log.w(TAG, "la cuenta ya no es válida: se cierra la sesión", e);
                cerrarSesion(context);
                siInvalida.run();
            }
        });
    }

    // ------------------------------------------------------------------ //
    // Correo y contraseña
    // ------------------------------------------------------------------ //

    public void iniciarSesion(@NonNull String correo, @NonNull String password,
                              @NonNull Resultado r) {
        auth.signInWithEmailAndPassword(correo, password)
                .addOnCompleteListener(t -> entregar(t, r));
    }

    /** Crea la cuenta y guarda el nombre visible. La sesión queda iniciada. */
    public void registrar(@NonNull String nombre, @NonNull String correo,
                         @NonNull String password, @NonNull Resultado r) {
        auth.createUserWithEmailAndPassword(correo, password).addOnCompleteListener(t -> {
            if (!t.isSuccessful() || t.getResult() == null || t.getResult().getUser() == null) {
                entregar(t, r);
                return;
            }
            UserProfileChangeRequest perfil = new UserProfileChangeRequest.Builder()
                    .setDisplayName(nombre)
                    .build();
            // Si falla guardar el nombre, la cuenta ya existe: se sigue igualmente
            t.getResult().getUser().updateProfile(perfil)
                    .addOnCompleteListener(p -> r.ok());
        });
    }

    /**
     * Envía el correo para restablecer la contraseña. Con la protección contra la
     * enumeración de correos, Firebase responde bien aunque el correo no tenga cuenta.
     */
    public void enviarRecuperacion(@NonNull String correo, @NonNull Resultado r) {
        auth.sendPasswordResetEmail(correo).addOnCompleteListener(t -> entregar(t, r));
    }

    // ------------------------------------------------------------------ //
    // Google (Credential Manager)
    // ------------------------------------------------------------------ //

    /**
     * "Continuar con Google": el selector de cuentas de Credential Manager devuelve un ID
     * token de Google que se cambia por la sesión de Firebase. La primera vez crea la cuenta.
     *
     * @param permitirNuevas false con registration_enabled=false: si la cuenta es nueva, se
     *                       borra al momento y llega {@link AuthErrores.Motivo#REGISTRO_DESACTIVADO}
     */
    public void iniciarConGoogle(@NonNull Activity activity, boolean permitirNuevas,
                                 @NonNull Resultado r) {
        pedirTokenGoogle(activity, new TokenGoogle() {
            @Override
            public void ok(@NonNull String idToken) {
                AuthCredential cred = GoogleAuthProvider.getCredential(idToken, null);
                auth.signInWithCredential(cred).addOnCompleteListener(t -> {
                    if (!t.isSuccessful() || t.getResult() == null) {
                        entregar(t, r);
                        return;
                    }
                    AdditionalUserInfo info = t.getResult().getAdditionalUserInfo();
                    FirebaseUser nuevo = t.getResult().getUser();
                    if (!permitirNuevas && info != null && info.isNewUser() && nuevo != null) {
                        // Registro cerrado: se deshace la cuenta recién creada
                        nuevo.delete().addOnCompleteListener(d -> {
                            cerrarSesion(activity);
                            r.error(AuthErrores.Motivo.REGISTRO_DESACTIVADO);
                        });
                        return;
                    }
                    r.ok();
                });
            }

            @Override
            public void error(@NonNull AuthErrores.Motivo motivo) {
                r.error(motivo);
            }
        });
    }

    /** Vuelve a confirmar la identidad con Google (antes de borrar la cuenta). */
    public void reautenticarConGoogle(@NonNull Activity activity, @NonNull Resultado r) {
        pedirTokenGoogle(activity, new TokenGoogle() {
            @Override
            public void ok(@NonNull String idToken) {
                reautenticar(GoogleAuthProvider.getCredential(idToken, null), r);
            }

            @Override
            public void error(@NonNull AuthErrores.Motivo motivo) {
                r.error(motivo);
            }
        });
    }

    private interface TokenGoogle {
        void ok(@NonNull String idToken);

        void error(@NonNull AuthErrores.Motivo motivo);
    }

    /**
     * Abre el selector de cuentas de Google. default_web_client_id lo genera el plugin
     * google-services a partir de app/google-services.json (cliente web de Firebase).
     */
    private void pedirTokenGoogle(@NonNull Activity activity, @NonNull TokenGoogle callback) {
        GetSignInWithGoogleOption opcion = new GetSignInWithGoogleOption.Builder(
                activity.getString(R.string.default_web_client_id)).build();
        GetCredentialRequest peticion = new GetCredentialRequest.Builder()
                .addCredentialOption(opcion)
                .build();
        CredentialManager.create(activity).getCredentialAsync(activity, peticion,
                new CancellationSignal(), ContextCompat.getMainExecutor(activity),
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse respuesta) {
                        Credential c = respuesta.getCredential();
                        if (c instanceof CustomCredential && GoogleIdTokenCredential
                                .TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(c.getType())) {
                            try {
                                callback.ok(GoogleIdTokenCredential.createFrom(c.getData()).getIdToken());
                                return;
                            } catch (Exception e) {
                                Log.w(TAG, "credencial de Google no válida", e);
                            }
                        }
                        callback.error(AuthErrores.Motivo.DESCONOCIDO);
                    }

                    @Override
                    public void onError(@NonNull GetCredentialException e) {
                        if (e instanceof GetCredentialCancellationException) {
                            callback.error(AuthErrores.Motivo.CANCELADO);
                        } else if (e instanceof NoCredentialException) {
                            callback.error(AuthErrores.Motivo.SIN_CUENTA_GOOGLE);
                        } else {
                            Log.w(TAG, "Credential Manager: " + e.getClass().getSimpleName(), e);
                            callback.error(AuthErrores.Motivo.DESCONOCIDO);
                        }
                    }
                });
    }

    // ------------------------------------------------------------------ //
    // Cerrar sesión y borrar la cuenta
    // ------------------------------------------------------------------ //

    /** Cierra la sesión. El historial local se conserva (por cuenta llega en la Fase 5). */
    public void cerrarSesion(@NonNull Context context) {
        auth.signOut();
        // Olvida la cuenta de Google elegida: la próxima vez vuelve a salir el selector
        CredentialManager.create(context).clearCredentialStateAsync(
                new ClearCredentialStateRequest(), null, ContextCompat.getMainExecutor(context),
                new CredentialManagerCallback<Void, ClearCredentialException>() {
                    @Override
                    public void onResult(Void v) {
                    }

                    @Override
                    public void onError(@NonNull ClearCredentialException e) {
                        Log.w(TAG, "no se pudo limpiar el estado de Credential Manager", e);
                    }
                });
    }

    /**
     * Borra la cuenta de Firebase Authentication. El historial local no se borra
     * (decisión del autor, 2026-11-12).
     *
     * Firebase exige un inicio de sesión reciente: si no lo es, llega
     * {@link AuthErrores.Motivo#REQUIERE_LOGIN_RECIENTE} y la pantalla pide
     * {@link #reautenticarConPassword} (o Google) antes de repetir.
     *
     * Fase 5: antes de borrar el usuario, llamar a DELETE /api/v1/me para que el backend
     * borre sus imágenes y metadatos de la nube.
     */
    public void borrarCuenta(@NonNull Resultado r) {
        FirebaseUser u = auth.getCurrentUser();
        if (u == null) {
            r.ok();
            return;
        }
        u.delete().addOnCompleteListener(t -> entregar(t, r));
    }

    public void reautenticarConPassword(@NonNull String password, @NonNull Resultado r) {
        FirebaseUser u = auth.getCurrentUser();
        if (u == null || u.getEmail() == null) {
            r.error(AuthErrores.Motivo.CREDENCIALES_INCORRECTAS);
            return;
        }
        reautenticar(EmailAuthProvider.getCredential(u.getEmail(), password), r);
    }

    void reautenticar(@NonNull AuthCredential credencial, @NonNull Resultado r) {
        FirebaseUser u = auth.getCurrentUser();
        if (u == null) {
            r.error(AuthErrores.Motivo.CREDENCIALES_INCORRECTAS);
            return;
        }
        u.reauthenticate(credencial).addOnCompleteListener(t -> entregar(t, r));
    }

    // ------------------------------------------------------------------ //
    // Token para el backend
    // ------------------------------------------------------------------ //

    /**
     * ID token de Firebase para "Authorization: Bearer". Bloquea: solo desde un hilo de
     * red (interceptor de OkHttp). Firebase lo renueva solo cuando está por caducar; con
     * {@code forzar} se pide uno nuevo (tras un 401 del backend).
     *
     * @return el token, o null sin sesión, sin conexión o si tarda demasiado
     */
    @Nullable
    public String tokenBloqueante(boolean forzar) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new IllegalStateException("tokenBloqueante() no puede usarse en el hilo principal");
        }
        FirebaseUser u = auth.getCurrentUser();
        if (u == null) return null;
        try {
            GetTokenResult res = Tasks.await(u.getIdToken(forzar), TOKEN_TIMEOUT_S, TimeUnit.SECONDS);
            return res.getToken();
        } catch (Exception e) {
            Log.w(TAG, "no se pudo obtener el token de Firebase", e);
            return null;
        }
    }

    // ------------------------------------------------------------------ //
    // Utilidades
    // ------------------------------------------------------------------ //

    private static void entregar(@NonNull Task<?> t, @NonNull Resultado r) {
        if (t.isSuccessful()) {
            r.ok();
        } else {
            r.error(motivo(t.getException()));
        }
    }

    @NonNull
    static AuthErrores.Motivo motivo(@Nullable Exception e) {
        if (e != null) Log.w(TAG, "error de Firebase Auth", e);
        if (e instanceof FirebaseNetworkException) return AuthErrores.Motivo.SIN_CONEXION;
        if (e instanceof FirebaseTooManyRequestsException) return AuthErrores.Motivo.DEMASIADOS_INTENTOS;
        if (e instanceof FirebaseAuthException) {
            return AuthErrores.desdeCodigo(((FirebaseAuthException) e).getErrorCode());
        }
        return AuthErrores.Motivo.DESCONOCIDO;
    }
}
