package com.irkana.ecovisionsv.ui;

import android.content.Intent;
import android.graphics.Color;
import android.view.View;
import android.view.ViewParent;
import android.widget.EditText;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputLayout;
import com.irkana.ecovisionsv.R;
import com.irkana.ecovisionsv.auth.AuthErrores;
import com.irkana.ecovisionsv.auth.AuthRepository;
import com.irkana.ecovisionsv.auth.ValidacionAuth;

/**
 * Base de las pantallas de login, registro y recuperación de contraseña:
 * barras del sistema sobre la cabecera verde, errores en los campos y mensajes de
 * Firebase en español.
 */
abstract class AuthBaseActivity extends AppCompatActivity {

    /** Llamar justo después de setContentView. */
    protected void configurarVentana() {
        // Íconos claros sobre la cabecera verde
        EdgeToEdge.enable(this, SystemBarStyle.dark(Color.TRANSPARENT));
        View raiz = findViewById(R.id.raiz_auth);
        View cabecera = findViewById(R.id.cabecera_auth);
        int padTop = cabecera.getPaddingTop();
        ViewCompat.setOnApplyWindowInsetsListener(raiz, (v, insets) -> {
            Insets barras = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.ime());
            v.setPadding(barras.left, 0, barras.right, barras.bottom);
            cabecera.setPadding(cabecera.getPaddingLeft(), padTop + barras.top,
                    cabecera.getPaddingRight(), cabecera.getPaddingBottom());
            return insets;
        });
    }

    /** Abre la pantalla principal y cierra todas las de auth. */
    protected void irAInicio() {
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    // ------------------------------------------------------------------ //
    // Campos
    // ------------------------------------------------------------------ //

    @NonNull
    protected static String texto(@NonNull EditText campo) {
        return campo.getText() == null ? "" : campo.getText().toString();
    }

    /** Muestra (o quita, con null) el error bajo el campo. @return true si hay error. */
    protected boolean marcarError(@NonNull EditText campo, @Nullable ValidacionAuth.Error error) {
        TextInputLayout capa = capa(campo);
        String mensaje = error == null ? null : mensaje(error);
        if (capa != null) {
            capa.setError(mensaje);
        } else {
            campo.setError(mensaje);
        }
        return error != null;
    }

    @NonNull
    private String mensaje(@NonNull ValidacionAuth.Error error) {
        switch (error) {
            case CORREO_INVALIDO:
                return getString(R.string.auth_correo_invalido);
            case PASSWORD_CORTA:
                return getString(R.string.auth_password_corta, ValidacionAuth.PASSWORD_MIN);
            case NO_COINCIDEN:
                return getString(R.string.auth_password_no_coinciden);
            case NOMBRE_LARGO:
                return getString(R.string.auth_nombre_largo, ValidacionAuth.NOMBRE_MAX);
            case VACIO:
            default:
                return getString(R.string.auth_campo_obligatorio);
        }
    }

    @Nullable
    private static TextInputLayout capa(@NonNull View campo) {
        ViewParent p = campo.getParent();
        while (p != null && !(p instanceof TextInputLayout)) p = p.getParent();
        return (TextInputLayout) p;
    }

    // ------------------------------------------------------------------ //
    // Errores de Firebase
    // ------------------------------------------------------------------ //

    /** Muestra el error de Firebase; CANCELADO no muestra nada. */
    protected void mostrarError(@NonNull AuthErrores.Motivo motivo) {
        if (motivo == AuthErrores.Motivo.CANCELADO) return;
        Snackbar.make(findViewById(android.R.id.content), mensaje(motivo), Snackbar.LENGTH_LONG)
                .show();
    }

    @StringRes
    static int mensaje(@NonNull AuthErrores.Motivo motivo) {
        switch (motivo) {
            case CREDENCIALES_INCORRECTAS: return R.string.auth_error_credenciales;
            case CORREO_INVALIDO: return R.string.auth_error_correo_invalido;
            case CORREO_EN_USO: return R.string.auth_error_correo_en_uso;
            case PASSWORD_DEBIL: return R.string.auth_error_password_debil;
            case USUARIO_DESACTIVADO: return R.string.auth_error_usuario_desactivado;
            case DEMASIADOS_INTENTOS: return R.string.auth_error_demasiados_intentos;
            case SIN_CONEXION: return R.string.auth_error_sin_conexion;
            case REQUIERE_LOGIN_RECIENTE: return R.string.auth_error_login_reciente;
            case PROVEEDOR_DESACTIVADO: return R.string.auth_error_proveedor;
            case REGISTRO_DESACTIVADO: return R.string.auth_error_registro_desactivado;
            case SIN_CUENTA_GOOGLE: return R.string.auth_error_sin_cuenta_google;
            case CANCELADO:
            case DESCONOCIDO:
            default:
                return R.string.auth_error_desconocido;
        }
    }

    // ------------------------------------------------------------------ //
    // Google
    // ------------------------------------------------------------------ //

    /**
     * "Continuar con Google". Con {@code permitirNuevas=false} (registration_enabled=false)
     * solo entran las cuentas que ya existen.
     */
    protected void entrarConGoogle(boolean permitirNuevas) {
        ocupado(true);
        AuthRepository.getInstance().iniciarConGoogle(this, permitirNuevas,
                new AuthRepository.Resultado() {
                    @Override
                    public void ok() {
                        irAInicio();
                    }

                    @Override
                    public void error(@NonNull AuthErrores.Motivo motivo) {
                        ocupado(false);
                        mostrarError(motivo);
                    }
                });
    }

    /** Bloquea el formulario mientras se espera a Firebase. */
    protected abstract void ocupado(boolean ocupado);

    /** Deshabilita (o habilita) varias vistas mientras se espera a Firebase. */
    protected static void habilitar(boolean habilitado, @NonNull View... vistas) {
        for (View v : vistas) {
            if (v != null) v.setEnabled(habilitado);
        }
    }
}
