package com.irkana.ecovisionsv.auth;

import androidx.annotation.Nullable;

/**
 * Traduce los códigos de error de Firebase Authentication a un motivo de la app.
 * Sin dependencias de Android ni de Firebase (se prueba con JUnit); la pantalla elige el
 * mensaje en español con {@link Motivo}.
 *
 * Con la protección contra la enumeración de correos de Firebase (activa por defecto),
 * "usuario no existe" y "contraseña incorrecta" llegan como ERROR_INVALID_CREDENTIAL: se
 * muestran con el mismo mensaje para no revelar qué correos tienen cuenta.
 */
public final class AuthErrores {

    public enum Motivo {
        CREDENCIALES_INCORRECTAS,
        CORREO_INVALIDO,
        CORREO_EN_USO,
        PASSWORD_DEBIL,
        USUARIO_DESACTIVADO,
        DEMASIADOS_INTENTOS,
        SIN_CONEXION,
        REQUIERE_LOGIN_RECIENTE,
        PROVEEDOR_DESACTIVADO,
        /** Cuenta nueva con registration_enabled=false (la app la deshace). */
        REGISTRO_DESACTIVADO,
        /** Google: el usuario cerró el selector de cuentas; no se muestra nada. */
        CANCELADO,
        /** Google: no hay ninguna cuenta de Google en el teléfono. */
        SIN_CUENTA_GOOGLE,
        DESCONOCIDO
    }

    private AuthErrores() {}

    public static Motivo desdeCodigo(@Nullable String codigo) {
        if (codigo == null) return Motivo.DESCONOCIDO;
        switch (codigo) {
            case "ERROR_INVALID_CREDENTIAL":
            case "ERROR_WRONG_PASSWORD":
            case "ERROR_USER_NOT_FOUND":
            case "ERROR_INVALID_USER_TOKEN":
                return Motivo.CREDENCIALES_INCORRECTAS;
            case "ERROR_INVALID_EMAIL":
                return Motivo.CORREO_INVALIDO;
            case "ERROR_EMAIL_ALREADY_IN_USE":
            case "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL":
            case "ERROR_CREDENTIAL_ALREADY_IN_USE":
                return Motivo.CORREO_EN_USO;
            case "ERROR_WEAK_PASSWORD":
                return Motivo.PASSWORD_DEBIL;
            case "ERROR_USER_DISABLED":
                return Motivo.USUARIO_DESACTIVADO;
            case "ERROR_TOO_MANY_REQUESTS":
                return Motivo.DEMASIADOS_INTENTOS;
            case "ERROR_NETWORK_REQUEST_FAILED":
                return Motivo.SIN_CONEXION;
            case "ERROR_REQUIRES_RECENT_LOGIN":
            case "ERROR_USER_TOKEN_EXPIRED":
                return Motivo.REQUIERE_LOGIN_RECIENTE;
            case "ERROR_OPERATION_NOT_ALLOWED":
                return Motivo.PROVEEDOR_DESACTIVADO;
            default:
                return Motivo.DESCONOCIDO;
        }
    }
}
