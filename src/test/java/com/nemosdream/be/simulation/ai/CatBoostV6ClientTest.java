package com.nemosdream.be.simulation.ai;

import static org.junit.jupiter.api.Assertions.*;

import com.nemosdream.be.simulation.domain.PaOutcome;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.ObjectMapper;

class CatBoostV6ClientTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final CatBoostV6State state = new CatBoostV6State("20250322HHKT02025", 5, "top", 0,
            "0", 2, 1, 54354, 50704, 63, 0);

    @Test
    void validatesStateAndDoesNotInferPitchCounts() {
        assertThrows(IllegalArgumentException.class, () -> new CatBoostV6State("20250322HHKT02025", 5,
                "top", 0, "0", 2, 1, 54354, 50704, 2, 3));
        assertEquals(63, ((Map<?, ?>) state.asRequest().get("state")).get("total_pitch_count_before"));
    }

    @Test
    void sendsExactStateAndRejectsBadDistributionOrVersion() throws Exception {
        AtomicReference<String> response = new AtomicReference<>(validResponse());
        AtomicReference<String> received = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/pa/predict-service", exchange -> {
            received.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = response.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            CatBoostV6Client client = new CatBoostV6Client(mapper, "http://127.0.0.1:" + server.getAddress().getPort(), 5000);
            Map<PaOutcome, Double> probabilities = client.probabilities(state);
            assertEquals(9, probabilities.size());
            assertEquals(0.0, probabilities.get(PaOutcome.DOUBLE_PLAY));
            assertEquals(state.asRequest(), mapper.readValue(received.get(), Map.class));
            assertThrows(IllegalStateException.class, () -> client.probabilities(state, new CatBoostHistory(293,57,0,0)));
            response.set(validResponse().replace(CatBoostV6Client.MODEL_VERSION, "wrong"));
            assertThrows(IllegalStateException.class, () -> client.probabilities(state));
            response.set(validResponse().replace("\"GO\":1.0", "\"GO\":0.5"));
            assertThrows(IllegalStateException.class, () -> client.probabilities(state));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void propagatesHttpFailuresAndTimeouts() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<Integer> mode = new AtomicReference<>(503);
        server.createContext("/v1/pa/predict-service", exchange -> {
            try {
                if (mode.get() == 200) Thread.sleep(1500);
                exchange.sendResponseHeaders(mode.get(), -1);
                exchange.close();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (java.io.IOException ignored) {
                exchange.close();
            }
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort();
            CatBoostV6Client client = new CatBoostV6Client(mapper, url, 300);
            assertThrows(IllegalStateException.class, () -> client.probabilities(state));
            mode.set(200);
            assertThrows(IllegalStateException.class, () -> client.probabilities(state));
        } finally {
            server.stop(0);
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "KBO_AI_INTEGRATION_URL", matches = ".+")
    void callsActualFastApi() {
        CatBoostV6Client client = new CatBoostV6Client(mapper, System.getenv("KBO_AI_INTEGRATION_URL"), 30000);
        Map<PaOutcome, Double> result = client.probabilities(state);
        assertEquals(9, result.size());
        assertEquals(1.0, result.values().stream().mapToDouble(Double::doubleValue).sum(), 1e-9);
        assertEquals(0.0, result.get(PaOutcome.DOUBLE_PLAY));
    }

    private String validResponse() {
        return "{\"model_version\":\"" + CatBoostV6Client.MODEL_VERSION
                + "\",\"feature_schema_version\":\"pa-service-v6-b-before-pitch-v1\","
                + "\"mapping_version\":\"direct-web9-spring-gdp-v1\","
                + "\"web_probabilities\":{\"1B\":0,\"2B\":0,\"3B\":0,\"BB\":0,"
                + "\"FO\":0,\"GDP\":0,\"GO\":1.0,\"HR\":0,\"SO\":0}}";
    }
}
