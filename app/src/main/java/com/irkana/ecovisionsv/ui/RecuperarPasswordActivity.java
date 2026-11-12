package com.irkana.ecovisionsv.ui;

import android.os.Bundle;
import android.widget.EditText;

import androidx.annotation.NonNull;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.irkana.ecovisionsv.R;
import com.irkana.ecovisionsv.auth.AuthErrores;
import com.irkana.ecovisionsv.auth.AuthRepository;
import com.irkana.ecovisionsv.auth.ValidacionAuth;

/**
 * Recuperar contraseña (Fase 3): Firebase envía un correo con el enlace para
 * restablecerla. El mensaje de éxito no confirma si el correo tiene cuenta.
 */
public class RecuperarPasswordActivity extends AuthBaseActivity {

    /** Correo ya escrito en el login, para no repetirlo. */
    static final String EXTRA_CORREO = "correo";

    private EditText campoEmail;
    private MaterialButton botonEnviar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);
        configurarVentana();

        campoEmail = findViewById(R.id.campo_email_forgot);
        botonEnviar = findViewById(R.id.boton_enviar_reset);
        String correo = getIntent().getStringExtra(EXTRA_CORREO);
        if (savedInstanceState == null && correo != null) campoEmail.setText(correo);

        botonEnviar.setOnClickListener(v -> enviar());
        findViewById(R.id.boton_volver_login).setOnClickListener(v -> finish());
        findViewById(R.id.boton_atras_forgot).setOnClickListener(v -> finish());
    }

    private void enviar() {
        String correo = ValidacionAuth.limpiar(texto(campoEmail));
        if (marcarError(campoEmail, ValidacionAuth.correo(correo))) return;

        ocupado(true);
        AuthRepository.getInstance().enviarRecuperacion(correo, new AuthRepository.Resultado() {
            @Override
            public void ok() {
                ocupado(false);
                new MaterialAlertDialogBuilder(RecuperarPasswordActivity.this)
                        .setTitle(R.string.forgot_enviado_titulo)
                        .setMessage(getString(R.string.forgot_enviado_mensaje, correo))
                        .setPositiveButton(R.string.forgot_volver_login, (d, w) -> finish())
                        .setCancelable(false)
                        .show();
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
        habilitar(!ocupado, campoEmail, botonEnviar);
        botonEnviar.setText(ocupado ? R.string.forgot_enviando : R.string.forgot_enviar);
    }
}
