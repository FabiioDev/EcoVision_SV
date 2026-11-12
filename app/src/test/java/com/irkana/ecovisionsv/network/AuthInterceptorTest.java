package com.irkana.ecovisionsv.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

public class AuthInterceptorTest {

    private MockWebServer servidor;
    /** Llamadas al proveedor: "false" = token normal, "true" = forzado. */
    private final List<Boolean> pedidos = new ArrayList<>();

    @Before
    public void setUp() throws Exception {
        servidor = new MockWebServer();
        servidor.start();
    }

    @After
    public void tearDown() throws Exception {
        servidor.shutdown();
    }

    private Response get(String ruta, AuthInterceptor.ProveedorToken proveedor) throws Exception {
        OkHttpClient cliente = new OkHttpClient.Builder()
                .addInterceptor(new AuthInterceptor(forzar -> {
                    pedidos.add(forzar);
                    return proveedor.token(forzar);
                }))
                .build();
        return cliente.newCall(new Request.Builder().url(servidor.url(ruta)).build()).execute();
    }

    @Test
    public void conSesion_anadeBearer() throws Exception {
        servidor.enqueue(new MockResponse().setResponseCode(200));
        try (Response r = get("/api/v1/config", forzar -> "tok1")) {
            assertEquals(200, r.code());
        }
        assertEquals("Bearer tok1", servidor.takeRequest().getHeader("Authorization"));
    }

    @Test
    public void sinSesion_saleSinCabecera() throws Exception {
        servidor.enqueue(new MockResponse().setResponseCode(200));
        get("/api/v1/config", forzar -> null).close();
        assertNull(servidor.takeRequest().getHeader("Authorization"));
    }

    @Test
    public void rutasPublicas_noPidenToken() throws Exception {
        servidor.enqueue(new MockResponse().setResponseCode(200));
        servidor.enqueue(new MockResponse().setResponseCode(200));
        get("/api/v1/config/public", forzar -> "tok1").close();
        get("/api/v1/health", forzar -> "tok1").close();
        assertNull(servidor.takeRequest().getHeader("Authorization"));
        assertNull(servidor.takeRequest().getHeader("Authorization"));
        assertTrue(pedidos.isEmpty());
    }

    @Test
    public void con401_renuevaYRepiteUnaVez() throws Exception {
        servidor.enqueue(new MockResponse().setResponseCode(401));
        servidor.enqueue(new MockResponse().setResponseCode(200));
        try (Response r = get("/api/v1/config", forzar -> forzar ? "tok2" : "tok1")) {
            assertEquals(200, r.code());
        }
        assertEquals("Bearer tok1", servidor.takeRequest().getHeader("Authorization"));
        RecordedRequest segunda = servidor.takeRequest();
        assertEquals("Bearer tok2", segunda.getHeader("Authorization"));
        assertEquals(List.of(false, true), pedidos);
        assertEquals(2, servidor.getRequestCount());
    }

    @Test
    public void con401_yMismoToken_noRepite() throws Exception {
        servidor.enqueue(new MockResponse().setResponseCode(401));
        try (Response r = get("/api/v1/config", forzar -> "tok1")) {
            assertEquals(401, r.code());
        }
        assertEquals(1, servidor.getRequestCount());
    }
}
