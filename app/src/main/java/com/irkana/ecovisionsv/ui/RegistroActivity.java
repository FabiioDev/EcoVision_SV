package com.irkana.ecovisionsv.ui;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.android.material.button.MaterialButton;
import com.irkana.ecovisionsv.R;
import com.irkana.ecovisionsv.auth.AuthErrores;
import com.irkana.ecovisionsv.auth.AuthRepository;
import com.irkana.ecovisionsv.auth.ValidacionAuth;
import com.irkana.ecovisionsv.config.ConfigRepository;

/**
 * Registro con correo y contraseña (Fase 3). Guarda el nombre como nombre visible de la
 * cuenta y entra directo a Inicio.
 *
 * Con registration_enabled=false no se abre (LoginActivity tampoco muestra el enlace).
 * Solo es una flag de la app: impedir el registro de verdad corresponde a Firebase/backend
 * (regla 5; anotado en ecovision_admin/TODO.txt).
 */
public class RegistroActivity extends AuthBaseActivity {

    private EditText campoNombre;
    private EditText campoEmail;
    private EditText campoPassword;
    private EditText campoConfirmar;
    private MaterialButton botonRegistrarse;
    private MaterialButton botonGoogle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!registroPermitido()) {
            Toast.makeText(this, R.string.auth_error_registro_desactivado, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        setContentView(R.layout.activity_register);
        configurarVentana();

        campoNombre = findViewById(R.id.campo_nombre);
        campoEmail = findViewById(R.id.campo_email_registro);
        campoPassword = findViewById(R.id.campo_password_registro);
        campoConfirmar = findViewById(R.id.campo_confirmar_password);
        botonRegistrarse = findViewById(R.id.boton_registrarse);
        botonGoogle = findViewById(R.id.boton_google_registro);

        botonRegistrarse.setOnClickListener(v -> registrar());
        findViewById(R.id.boton_atras_registro).setOnClickListener(v -> finish());
        findViewById(R.id.enlace_login).setOnClickListener(v -> finish());
    }

    private boolean registroPermitido() {
        return ConfigRepository.getInstance(this).get().isRegistrationEnabled();
    }

    private void registrar() {
        if (!registroPermitido()) {
            mostrarError(AuthErrores.Motivo.REGISTRO_DESACTIVADO);
            return;
        }
        String nombre = ValidacionAuth.limpiar(texto(campoNombre));
        String correo = ValidacionAuth.limpiar(texto(campoEmail));
        String password = texto(campoPassword);
        boolean error = marcarError(campoNombre, ValidacionAuth.nombre(nombre));
        error |= marcarError(campoEmail, ValidacionAuth.correo(correo));
        error |= marcarError(campoPassword, ValidacionAuth.passwordNueva(password));
        error |= marcarError(campoConfirmar,
                ValidacionAuth.confirmacion(password, texto(campoConfirmar)));
        if (error) return;

        ocupado(true);
        AuthRepository.getInstance().registrar(nombre, correo, password, new AuthRepository.Resultado() {
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

    private void ocupado(boolean ocupado) {
        habilitar(!ocupado, campoNombre, campoEmail, campoPassword, campoConfirmar,
                botonRegistrarse, botonGoogle, findViewById(R.id.enlace_login));
        botonRegistrarse.setText(ocupado ? R.string.registro_creando : R.string.registro_crear_cuenta);
    }
}
