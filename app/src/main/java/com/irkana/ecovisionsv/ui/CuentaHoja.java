package com.irkana.ecovisionsv.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseUser;
import com.irkana.ecovisionsv.R;
import com.irkana.ecovisionsv.auth.AuthErrores;
import com.irkana.ecovisionsv.auth.AuthRepository;
import com.irkana.ecovisionsv.auth.ValidacionAuth;

import java.util.Locale;

/**
 * Hoja inferior "Tu cuenta" (sheet_cuenta.xml), abierta desde la foto de perfil de la
 * barra superior: nombre, correo y proveedor; "Cerrar sesión" y "Borrar cuenta".
 *
 * Borrar la cuenta pide confirmación. Si Firebase exige un inicio de sesión reciente, se
 * vuelve a confirmar la identidad (contraseña o selector de Google) y se repite. Solo se
 * borra la cuenta: el historial local se queda en el teléfono (decisión del autor,
 * 2026-11-12). Las imágenes de la nube (Fase 5) las borrará el backend con DELETE /me.
 */
final class CuentaHoja {

    /** Qué hacer al terminar sin sesión (cerrada o cuenta borrada): ir al login. */
    interface AlSalir {
        void salir(@NonNull String mensaje);
    }

    private final Activity activity;
    private final AlSalir alSalir;
    private final AuthRepository auth = AuthRepository.getInstance();
    private AlertDialog progreso;

    CuentaHoja(@NonNull Activity activity, @NonNull AlSalir alSalir) {
        this.activity = activity;
        this.alSalir = alSalir;
    }

    /** Nombre visible de la cuenta; si no tiene, el correo. */
    @NonNull
    static String nombreVisible(@NonNull Context context, @NonNull FirebaseUser u) {
        String nombre = ValidacionAuth.limpiar(u.getDisplayName());
        if (!nombre.isEmpty()) return nombre;
        String correo = ValidacionAuth.limpiar(u.getEmail());
        return !correo.isEmpty() ? correo : context.getString(R.string.cuenta_sin_nombre);
    }

    /** Inicial del avatar (barra superior y hoja), en mayúscula. */
    @NonNull
    static String inicial(@NonNull Context context, @NonNull FirebaseUser u) {
        String visible = nombreVisible(context, u);
        return visible.substring(0, visible.offsetByCodePoints(0, 1))
                .toUpperCase(Locale.getDefault());
    }

    void mostrar() {
        FirebaseUser u = auth.usuario();
        if (u == null) {
            alSalir.salir(activity.getString(R.string.cuenta_sesion_invalida));
            return;
        }
        BottomSheetDialog hoja = new BottomSheetDialog(activity);
        View vista = LayoutInflater.from(activity).inflate(R.layout.sheet_cuenta, null, false);
        hoja.setContentView(vista);

        String correo = ValidacionAuth.limpiar(u.getEmail());
        String visible = nombreVisible(activity, u);
        ((TextView) vista.findViewById(R.id.inicial_cuenta)).setText(inicial(activity, u));
        ((TextView) vista.findViewById(R.id.nombre_cuenta)).setText(visible);
        TextView campoCorreo = vista.findViewById(R.id.correo_cuenta);
        campoCorreo.setText(correo);
        campoCorreo.setVisibility(correo.isEmpty() || correo.equals(visible) ? View.GONE : View.VISIBLE);
        ((TextView) vista.findViewById(R.id.proveedor_cuenta)).setText(
                AuthRepository.PROVEEDOR_GOOGLE.equals(auth.proveedor())
                        ? R.string.cuenta_con_google : R.string.cuenta_con_correo);

        vista.findViewById(R.id.boton_cerrar_sesion).setOnClickListener(v -> {
            hoja.dismiss();
            auth.cerrarSesion(activity);
            alSalir.salir("");
        });
        vista.findViewById(R.id.boton_borrar_cuenta).setOnClickListener(v -> {
            hoja.dismiss();
            confirmarBorrado();
        });
        hoja.show();
    }

    // ------------------------------------------------------------------ //
    // Borrar la cuenta
    // ------------------------------------------------------------------ //

    private void confirmarBorrado() {
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.cuenta_borrar_titulo)
                .setMessage(R.string.cuenta_borrar_mensaje)
                .setNegativeButton(R.string.historial_eliminar_cancelar, null)
                .setPositiveButton(R.string.cuenta_borrar_confirmar, (d, w) -> borrar(true))
                .show();
    }

    /** @param reintentarConReautenticacion false tras reautenticar (evita bucles) */
    private void borrar(boolean reintentarConReautenticacion) {
        mostrarProgreso();
        auth.borrarCuenta(new AuthRepository.Resultado() {
            @Override
            public void ok() {
                ocultarProgreso();
                auth.cerrarSesion(activity);
                alSalir.salir(activity.getString(R.string.cuenta_borrada));
            }

            @Override
            public void error(@NonNull AuthErrores.Motivo motivo) {
                ocultarProgreso();
                if (motivo == AuthErrores.Motivo.REQUIERE_LOGIN_RECIENTE
                        && reintentarConReautenticacion) {
                    reautenticar();
                } else {
                    avisar(motivo);
                }
            }
        });
    }

    private void reautenticar() {
        if (AuthRepository.PROVEEDOR_GOOGLE.equals(auth.proveedor())) {
            new MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.cuenta_confirmar_titulo)
                    .setMessage(R.string.cuenta_confirmar_google)
                    .setNegativeButton(R.string.historial_eliminar_cancelar, null)
                    .setPositiveButton(R.string.cuenta_continuar, (d, w) ->
                            auth.reautenticarConGoogle(activity, trasReautenticar()))
                    .show();
        } else {
            pedirPassword();
        }
    }

    private void pedirPassword() {
        View contenido = LayoutInflater.from(activity).inflate(R.layout.dialog_password, null, false);
        EditText campo = contenido.findViewById(R.id.campo_password_confirmar);
        TextInputLayout capa = contenido.findViewById(R.id.capa_password_confirmar);
        AlertDialog dialogo = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.cuenta_confirmar_titulo)
                .setMessage(R.string.cuenta_confirmar_mensaje)
                .setView(contenido)
                .setNegativeButton(R.string.historial_eliminar_cancelar, null)
                .setPositiveButton(R.string.cuenta_borrar_confirmar, null)
                .show();
        // Se valida antes de cerrar el diálogo
        dialogo.getButton(Dialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String password = campo.getText() == null ? "" : campo.getText().toString();
            if (ValidacionAuth.passwordLogin(password) != null) {
                capa.setError(activity.getString(R.string.auth_campo_obligatorio));
                return;
            }
            dialogo.dismiss();
            mostrarProgreso();
            auth.reautenticarConPassword(password, trasReautenticar());
        });
    }

    @NonNull
    private AuthRepository.Resultado trasReautenticar() {
        return new AuthRepository.Resultado() {
            @Override
            public void ok() {
                borrar(false);
            }

            @Override
            public void error(@NonNull AuthErrores.Motivo motivo) {
                ocultarProgreso();
                avisar(motivo);
            }
        };
    }

    // ------------------------------------------------------------------ //
    // Utilidades
    // ------------------------------------------------------------------ //

    private void avisar(@NonNull AuthErrores.Motivo motivo) {
        if (motivo == AuthErrores.Motivo.CANCELADO || activity.isFinishing()) return;
        Toast.makeText(activity, AuthBaseActivity.mensaje(motivo), Toast.LENGTH_LONG).show();
    }

    private void mostrarProgreso() {
        if (progreso != null && progreso.isShowing()) return;
        progreso = new MaterialAlertDialogBuilder(activity)
                .setMessage(R.string.cuenta_borrando)
                .setCancelable(false)
                .show();
    }

    private void ocultarProgreso() {
        if (progreso != null && progreso.isShowing() && !activity.isFinishing()) {
            progreso.dismiss();
        }
        progreso = null;
    }
}
