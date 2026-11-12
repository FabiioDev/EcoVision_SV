package com.irkana.ecovisionsv.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.material.button.MaterialButton;
import com.irkana.ecovisionsv.R;
import com.irkana.ecovisionsv.auth.AuthErrores;
import com.irkana.ecovisionsv.auth.AuthRepository;
import com.irkana.ecovisionsv.auth.ValidacionAuth;
import com.irkana.ecovisionsv.config.ConfigRepository;

/**
 * Inicio de sesión (Fase 3). El login es obligatorio (D2): MainActivity abre esta
 * pantalla si no hay sesión. Con sesión iniciada pasa directo a Inicio.
 */
public class LoginActivity extends AuthBaseActivity {

    private EditText campoEmail;
    private EditText campoPassword;
    private MaterialButton botonLogin;
    private MaterialButton botonGoogle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (AuthRepository.getInstance().haySesion()) {
            irAInicio();
            return;
        }
        setContentView(R.layout.activity_login);
        configurarVentana();

        campoEmail = findViewById(R.id.campo_email);
        campoPassword = findViewById(R.id.campo_password);
        botonLogin = findViewById(R.id.boton_login);
        botonGoogle = findViewById(R.id.boton_google);

        botonLogin.setOnClickListener(v -> iniciarSesion());
        // Con el registro cerrado, Google solo deja entrar a las cuentas que ya existen
        botonGoogle.setOnClickListener(v -> entrarConGoogle(
                ConfigRepository.getInstance(this).isRegistroPermitido()));
        campoPassword.setOnEditorActionListener((v, accion, e) -> {
            if (accion != EditorInfo.IME_ACTION_DONE) return false;
            iniciarSesion();
            return true;
        });
        findViewById(R.id.enlace_olvide_password).setOnClickListener(v ->
                startActivity(new Intent(this, RecuperarPasswordActivity.class)
                        .putExtra(RecuperarPasswordActivity.EXTRA_CORREO,
                                ValidacionAuth.limpiar(texto(campoEmail)))));
        findViewById(R.id.enlace_registro).setOnClickListener(v ->
                startActivity(new Intent(this, RegistroActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (campoEmail == null) return; // ya se fue a Inicio en onCreate
        // Primero lo guardado (sin esperar a la red) y luego lo que diga el backend
        aplicarConfigPublica();
        ConfigRepository.getInstance(this).consultarPublica(p -> {
            if (!isFinishing() && !isDestroyed()) aplicarConfigPublica();
        });
    }

    /**
     * Antes del login (config pública o, sin conexión, la guardada):
     *  - registration_enabled=false: se oculta "¿No tienes cuenta? Regístrate";
     *  - maintenance_mode=true: aviso con maintenance_message. Solo informa: se puede
     *    entrar; lo que se bloquea en Inicio es la detección.
     */
    private void aplicarConfigPublica() {
        ConfigRepository config = ConfigRepository.getInstance(this);
        findViewById(R.id.contenedor_registro).setVisibility(
                config.isRegistroPermitido() ? View.VISIBLE : View.INVISIBLE);
        boolean mantenimiento = config.isMantenimientoAntesDeLogin();
        findViewById(R.id.aviso_mantenimiento_login).setVisibility(
                mantenimiento ? View.VISIBLE : View.GONE);
        if (mantenimiento) {
            ((TextView) findViewById(R.id.texto_mantenimiento_login))
                    .setText(config.getMensajeMantenimientoAntesDeLogin());
        }
    }

    private void iniciarSesion() {
        String correo = ValidacionAuth.limpiar(texto(campoEmail));
        String password = texto(campoPassword);
        boolean error = marcarError(campoEmail, ValidacionAuth.correo(correo));
        error |= marcarError(campoPassword, ValidacionAuth.passwordLogin(password));
        if (error) return;

        ocupado(true);
        AuthRepository.getInstance().iniciarSesion(correo, password, new AuthRepository.Resultado() {
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

    @Override
    protected void ocupado(boolean ocupado) {
        habilitar(!ocupado, campoEmail, campoPassword, botonLogin, botonGoogle,
                findViewById(R.id.enlace_olvide_password), findViewById(R.id.enlace_registro));
        botonLogin.setText(ocupado ? R.string.login_iniciando : R.string.login_iniciar);
    }
}
