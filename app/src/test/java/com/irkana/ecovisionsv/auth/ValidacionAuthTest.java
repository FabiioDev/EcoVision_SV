package com.irkana.ecovisionsv.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.irkana.ecovisionsv.auth.ValidacionAuth.Error;

import org.junit.Test;

public class ValidacionAuthTest {

    @Test
    public void correo() {
        assertNull(ValidacionAuth.correo("ana@correo.com"));
        assertNull(ValidacionAuth.correo("  ana@correo.com  "));
        assertEquals(Error.VACIO, ValidacionAuth.correo(null));
        assertEquals(Error.VACIO, ValidacionAuth.correo("   "));
        assertEquals(Error.CORREO_INVALIDO, ValidacionAuth.correo("ana"));
        assertEquals(Error.CORREO_INVALIDO, ValidacionAuth.correo("ana@correo"));
        assertEquals(Error.CORREO_INVALIDO, ValidacionAuth.correo("ana maria@correo.com"));
    }

    @Test
    public void passwordLogin_soloNoVacia() {
        assertNull(ValidacionAuth.passwordLogin("1"));
        assertEquals(Error.VACIO, ValidacionAuth.passwordLogin(""));
    }

    @Test
    public void passwordNueva_minimoDeFirebase() {
        assertNull(ValidacionAuth.passwordNueva("123456"));
        assertEquals(Error.PASSWORD_CORTA, ValidacionAuth.passwordNueva("12345"));
        assertEquals(Error.VACIO, ValidacionAuth.passwordNueva(null));
    }

    @Test
    public void confirmacion() {
        assertNull(ValidacionAuth.confirmacion("secreto1", "secreto1"));
        assertEquals(Error.NO_COINCIDEN, ValidacionAuth.confirmacion("secreto1", "secreto2"));
        assertEquals(Error.VACIO, ValidacionAuth.confirmacion("secreto1", ""));
    }

    @Test
    public void nombre() {
        assertNull(ValidacionAuth.nombre(" Ana López "));
        assertEquals(Error.VACIO, ValidacionAuth.nombre(" "));
        assertEquals(Error.NOMBRE_LARGO, ValidacionAuth.nombre("x".repeat(61)));
    }
}
