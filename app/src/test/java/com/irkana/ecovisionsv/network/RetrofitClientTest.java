package com.irkana.ecovisionsv.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class RetrofitClientTest {

    @Test
    public void timeoutDeLectura_predictUsaLaConfigYHealthEsCorto() {
        assertEquals(60, RetrofitClient.timeoutLecturaS("/api/v1/predict", 60));
        assertEquals(120, RetrofitClient.timeoutLecturaS("/api/v1/predict", 120));
        assertEquals(8, RetrofitClient.timeoutLecturaS("/api/v1/health", 60));
    }

    @Test
    public void normalizarUrl() {
        assertEquals("https://ejemplo.run.app/", RetrofitClient.normalizarUrl(" ejemplo.run.app "));
        assertEquals("https://ejemplo.run.app/", RetrofitClient.normalizarUrl("https://ejemplo.run.app/"));
        assertEquals("http://10.0.2.2:8081/", RetrofitClient.normalizarUrl("http://10.0.2.2:8081"));
        assertNull(RetrofitClient.normalizarUrl("  "));
        assertNull(RetrofitClient.normalizarUrl(null));
    }
}
