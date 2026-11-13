package com.irkana.ecovisionsv.network;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Traduce una respuesta de error de POST /api/v1/predict (contrato:
 * ecovision_admin/docs/openapi.yaml) a un motivo de la app. Sin dependencias de Android
 * (se prueba con JUnit); el texto en español lo elige InferenciaRepository.
 *
 * El backend responde {"detail": "..."} con mensajes ya en español. Se usan tal cual cuando
 * aportan información (segundos de espera del límite, mensaje de mantenimiento, motivo
 * del rechazo de la imagen); en el resto se usa un texto fijo de la app.
 */
public final class ErrorDeteccion {

    public enum Tipo {
        /** 401: token no válido aunque se renovó. */
        SESION,
        /** 403: usuario desactivado desde el panel. */
        CUENTA_DESACTIVADA,
        /** 429: límite de detecciones por usuario. */
        LIMITE,
        /** 503: mantenimiento (detail = maintenance_message) o servicio sin configurar. */
        NO_DISPONIBLE,
        /** 413 */
        IMAGEN_GRANDE,
        /** 415 */
        FORMATO,
        /** Otros 4xx (imagen vacía o que el modelo no puede leer). */
        IMAGEN_RECHAZADA,
        /** 502: el backend no pudo contactar el modelo o este falló. */
        MODELO,
        /** 504 del backend o timeout de la app. */
        MODELO_LENTO,
        /** Cualquier otro código. */
        SERVIDOR
    }

    @NonNull public final Tipo tipo;
    public final int codigo;
    /** "detail" del backend si conviene mostrarlo; null si se usa el texto fijo. */
    @Nullable public final String detalle;

    private ErrorDeteccion(@NonNull Tipo tipo, int codigo, @Nullable String detalle) {
        this.tipo = tipo;
        this.codigo = codigo;
        this.detalle = detalle;
    }

    @NonNull
    public static ErrorDeteccion desdeRespuesta(int codigo, @Nullable String cuerpo) {
        String detail = detail(cuerpo);
        switch (codigo) {
            case 401: return new ErrorDeteccion(Tipo.SESION, codigo, null);
            case 403: return new ErrorDeteccion(Tipo.CUENTA_DESACTIVADA, codigo, null);
            case 429: return new ErrorDeteccion(Tipo.LIMITE, codigo, detail);
            case 503: return new ErrorDeteccion(Tipo.NO_DISPONIBLE, codigo, detail);
            case 413: return new ErrorDeteccion(Tipo.IMAGEN_GRANDE, codigo, null);
            case 415: return new ErrorDeteccion(Tipo.FORMATO, codigo, null);
            case 502: return new ErrorDeteccion(Tipo.MODELO, codigo, null);
            case 504: return new ErrorDeteccion(Tipo.MODELO_LENTO, codigo, null);
            default:
                if (codigo >= 400 && codigo < 500) {
                    return new ErrorDeteccion(Tipo.IMAGEN_RECHAZADA, codigo, detail);
                }
                return new ErrorDeteccion(Tipo.SERVIDOR, codigo, null);
        }
    }

    /** La app no recibió respuesta a tiempo (request_timeout_s). */
    @NonNull
    public static ErrorDeteccion timeout() {
        return new ErrorDeteccion(Tipo.MODELO_LENTO, 0, null);
    }

    /** "detail" si es un texto no vacío (FastAPI usa una lista en los 422 de validación). */
    @Nullable
    static String detail(@Nullable String cuerpo) {
        if (cuerpo == null || cuerpo.trim().isEmpty()) return null;
        try {
            JsonElement el = JsonParser.parseString(cuerpo);
            if (!el.isJsonObject()) return null;
            JsonObject o = el.getAsJsonObject();
            JsonElement d = o.get("detail");
            if (d == null || !d.isJsonPrimitive() || !d.getAsJsonPrimitive().isString()) return null;
            String texto = d.getAsString().trim();
            return texto.isEmpty() ? null : texto;
        } catch (RuntimeException e) {
            return null;
        }
    }
}
