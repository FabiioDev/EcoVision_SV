package com.irkana.ecovisionsv.auth;

import static org.junit.Assert.assertEquals;

import com.irkana.ecovisionsv.auth.AuthErrores.Motivo;

import org.junit.Test;

public class AuthErroresTest {

    @Test
    public void credencialesIncorrectas_mismoMensajeSinRevelarSiExisteLaCuenta() {
        assertEquals(Motivo.CREDENCIALES_INCORRECTAS, AuthErrores.desdeCodigo("ERROR_INVALID_CREDENTIAL"));
        assertEquals(Motivo.CREDENCIALES_INCORRECTAS, AuthErrores.desdeCodigo("ERROR_WRONG_PASSWORD"));
        assertEquals(Motivo.CREDENCIALES_INCORRECTAS, AuthErrores.desdeCodigo("ERROR_USER_NOT_FOUND"));
    }

    @Test
    public void codigosConocidos() {
        assertEquals(Motivo.CORREO_INVALIDO, AuthErrores.desdeCodigo("ERROR_INVALID_EMAIL"));
        assertEquals(Motivo.CORREO_EN_USO, AuthErrores.desdeCodigo("ERROR_EMAIL_ALREADY_IN_USE"));
        assertEquals(Motivo.CORREO_EN_USO,
                AuthErrores.desdeCodigo("ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL"));
        assertEquals(Motivo.PASSWORD_DEBIL, AuthErrores.desdeCodigo("ERROR_WEAK_PASSWORD"));
        assertEquals(Motivo.USUARIO_DESACTIVADO, AuthErrores.desdeCodigo("ERROR_USER_DISABLED"));
        assertEquals(Motivo.DEMASIADOS_INTENTOS, AuthErrores.desdeCodigo("ERROR_TOO_MANY_REQUESTS"));
        assertEquals(Motivo.SIN_CONEXION, AuthErrores.desdeCodigo("ERROR_NETWORK_REQUEST_FAILED"));
        assertEquals(Motivo.REQUIERE_LOGIN_RECIENTE, AuthErrores.desdeCodigo("ERROR_REQUIRES_RECENT_LOGIN"));
        assertEquals(Motivo.PROVEEDOR_DESACTIVADO, AuthErrores.desdeCodigo("ERROR_OPERATION_NOT_ALLOWED"));
    }

    @Test
    public void codigoNuloODesconocido() {
        assertEquals(Motivo.DESCONOCIDO, AuthErrores.desdeCodigo(null));
        assertEquals(Motivo.DESCONOCIDO, AuthErrores.desdeCodigo("ERROR_ALGO_NUEVO"));
    }
}
