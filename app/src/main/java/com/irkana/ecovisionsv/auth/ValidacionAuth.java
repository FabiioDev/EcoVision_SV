package com.irkana.ecovisionsv.auth;

import androidx.annotation.Nullable;

import java.util.regex.Pattern;

/**
 * Validación de los formularios de login, registro y recuperación antes de llamar a
 * Firebase. Sin dependencias de Android (se prueba con JUnit). Firebase vuelve a validar.
 */
public final class ValidacionAuth {

    /** Mínimo de Firebase Authentication para correo/contraseña. */
    public static final int PASSWORD_MIN = 6;

    public static final int NOMBRE_MAX = 60;

    /** Comprobación sencilla: algo@algo.algo, sin espacios. */
    private static final Pattern CORREO = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    public enum Error { VACIO, CORREO_INVALIDO, PASSWORD_CORTA, NO_COINCIDEN, NOMBRE_LARGO }

    private ValidacionAuth() {}

    /** null si es válido. */
    @Nullable
    public static Error correo(@Nullable String correo) {
        String c = limpiar(correo);
        if (c.isEmpty()) return Error.VACIO;
        return CORREO.matcher(c).matches() ? null : Error.CORREO_INVALIDO;
    }

    /** Login: solo que no esté vacía (la longitud la decide Firebase). */
    @Nullable
    public static Error passwordLogin(@Nullable String password) {
        return password == null || password.isEmpty() ? Error.VACIO : null;
    }

    /** Registro: mínimo {@link #PASSWORD_MIN} caracteres. */
    @Nullable
    public static Error passwordNueva(@Nullable String password) {
        if (password == null || password.isEmpty()) return Error.VACIO;
        return password.length() < PASSWORD_MIN ? Error.PASSWORD_CORTA : null;
    }

    @Nullable
    public static Error confirmacion(@Nullable String password, @Nullable String confirmacion) {
        if (confirmacion == null || confirmacion.isEmpty()) return Error.VACIO;
        return confirmacion.equals(password) ? null : Error.NO_COINCIDEN;
    }

    @Nullable
    public static Error nombre(@Nullable String nombre) {
        String n = limpiar(nombre);
        if (n.isEmpty()) return Error.VACIO;
        return n.length() > NOMBRE_MAX ? Error.NOMBRE_LARGO : null;
    }

    /** Recorta espacios (correo y nombre; la contraseña se usa tal cual). */
    public static String limpiar(@Nullable String texto) {
        return texto == null ? "" : texto.trim();
    }
}
