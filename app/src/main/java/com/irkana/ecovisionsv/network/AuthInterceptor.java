package com.irkana.ecovisionsv.network;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Añade "Authorization: Bearer &lt;idToken&gt;" (Firebase) a las peticiones al backend
 * (contrato: securitySchemes.firebaseBearer).
 *
 *  - Sin sesión, la petición sale sin cabecera (el backend responde 401 donde haga falta y
 *    la app usa la config guardada).
 *  - Las rutas sin autenticación del contrato (/api/v1/health, /api/v1/config/public) salen
 *    sin token: no hace falta esperar a Firebase.
 *  - Ante un 401 se pide un token nuevo (forzando la renovación) y se repite UNA vez.
 *
 * Lo usan {@link BackendClient} (config) y {@link RetrofitClient} (detección): las dos
 * hablan con el backend.
 */
public final class AuthInterceptor implements Interceptor {

    /** Origen del token; en la app, AuthRepository.tokenBloqueante. */
    public interface ProveedorToken {
        /** Token actual (o renovado con {@code forzar}); null sin sesión o si falla. */
        @Nullable
        String token(boolean forzar);
    }

    static final String CABECERA = "Authorization";

    private final ProveedorToken proveedor;

    public AuthInterceptor(@NonNull ProveedorToken proveedor) {
        this.proveedor = proveedor;
    }

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request original = chain.request();
        if (original.header(CABECERA) != null || esPublica(original.url().encodedPath())) {
            return chain.proceed(original);
        }

        String token = proveedor.token(false);
        if (token == null) return chain.proceed(original);

        Response resp = chain.proceed(conToken(original, token));
        if (resp.code() != 401) return resp;

        // Token caducado o revocado: se renueva y se intenta una sola vez más
        String nuevo = proveedor.token(true);
        if (nuevo == null || nuevo.equals(token)) return resp;
        resp.close();
        return chain.proceed(conToken(original, nuevo));
    }

    static boolean esPublica(@NonNull String ruta) {
        return ruta.endsWith("/api/v1/health") || ruta.endsWith("/api/v1/config/public");
    }

    private static Request conToken(@NonNull Request r, @NonNull String token) {
        return r.newBuilder().header(CABECERA, "Bearer " + token).build();
    }
}
