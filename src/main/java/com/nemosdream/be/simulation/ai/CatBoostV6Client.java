package com.nemosdream.be.simulation.ai;

import com.nemosdream.be.simulation.domain.PaOutcome;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** 독립적인 v6 호출 경계. Log5나 경기 진행 엔진의 동작을 자동으로 바꾸지 않는다. */
@Component
public class CatBoostV6Client {
    public static final String MODEL_VERSION = "catboost-pa-service-v6-b-7e1e4a64634c";
    private static final Set<String> CLASSES = Set.of("1B", "2B", "3B", "BB", "FO", "GDP", "GO", "HR", "SO");
    private final ObjectMapper mapper;
    private final HttpClient http;
    private final URI endpoint;
    private final Duration timeout;

    public CatBoostV6Client(ObjectMapper mapper,
                            @Value("${app.ai.base-url:http://localhost:8000}") String baseUrl,
                            @Value("${app.ai.timeout-ms:5000}") long timeoutMs) {
        this.mapper = mapper;
        if (timeoutMs <= 0) throw new IllegalArgumentException("AI timeout must be positive");
        this.timeout = Duration.ofMillis(timeoutMs);
        URI base = URI.create(baseUrl.endsWith("/") ? baseUrl : baseUrl + "/");
        if (!("http".equals(base.getScheme()) || "https".equals(base.getScheme())) || base.getHost() == null) {
            throw new IllegalArgumentException("Invalid AI base URL");
        }
        this.endpoint = base.resolve("v1/pa/predict-service");
        this.http = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    public Map<PaOutcome, Double> probabilities(CatBoostV6State state) {
        HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(state.asRequest())))
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw new IllegalStateException("AI HTTP status " + response.statusCode());
            Map<?, ?> body = mapper.readValue(response.body(), Map.class);
            if (!MODEL_VERSION.equals(body.get("model_version"))
                    || !"pa-service-v6-b-before-pitch-v1".equals(body.get("feature_schema_version"))
                    || !"direct-web9-spring-gdp-v1".equals(body.get("mapping_version"))) {
                throw new IllegalStateException("Unexpected AI model or schema version");
            }
            if (!(body.get("web_probabilities") instanceof Map<?, ?> raw)
                    || !new HashSet<>(raw.keySet()).equals(CLASSES)) {
                throw new IllegalStateException("Invalid AI outcome classes");
            }
            EnumMap<PaOutcome, Double> result = new EnumMap<>(PaOutcome.class);
            double sum = 0;
            for (String code : CLASSES) {
                Object value = raw.get(code);
                if (!(value instanceof Number n) || !Double.isFinite(n.doubleValue())
                        || n.doubleValue() < 0 || n.doubleValue() > 1) {
                    throw new IllegalStateException("Invalid AI probability: " + code);
                }
                double probability = n.doubleValue();
                result.put(PaOutcome.fromCode(code), probability);
                sum += probability;
            }
            if (Math.abs(sum - 1) > 1e-9 || ((state.outCount() == 2 || !state.baseState().contains("1"))
                    && result.get(PaOutcome.DOUBLE_PLAY) != 0.0)) {
                throw new IllegalStateException("Invalid AI probability distribution");
            }
            return Map.copyOf(result);
        } catch (IOException e) {
            throw new IllegalStateException("AI request failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("AI request interrupted", e);
        }
    }
}
