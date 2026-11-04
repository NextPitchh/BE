package com.nemosdream.be.bandit.client;

import com.nemosdream.be.bandit.dto.BanditPreparedRequest;
import com.nemosdream.be.bandit.dto.BanditRanking;
import com.nemosdream.be.bandit.dto.BanditRecommendationResponse;
import com.nemosdream.be.bandit.repository.BanditRepository;
import com.nemosdream.be.global.error.BusinessException;
import com.nemosdream.be.global.error.ErrorCode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class BanditClient {
    private final ObjectMapper mapper;
    private final HttpClient http;
    private final URI endpoint;
    private final Duration timeout;

    public BanditClient(ObjectMapper mapper, @Value("${app.ai.base-url:http://localhost:8000}") String baseUrl,
                        @Value("${app.ai.timeout-ms:5000}") long timeoutMs) {
        if (timeoutMs<=0) throw new IllegalArgumentException("AI timeout must be positive");
        URI base=URI.create(baseUrl.endsWith("/")?baseUrl:baseUrl+"/");
        if (!("http".equals(base.getScheme()) || "https".equals(base.getScheme())) || base.getHost()==null)
            throw new IllegalArgumentException("Invalid AI base URL");
        this.endpoint=base.resolve("v1/bandit/recommend"); this.timeout=Duration.ofMillis(timeoutMs);
        this.http=HttpClient.newBuilder().connectTimeout(timeout).build(); this.mapper=mapper;
    }

    public BanditRecommendationResponse recommend(BanditPreparedRequest prepared) {
        HttpRequest request=HttpRequest.newBuilder(endpoint).timeout(timeout).header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(prepared.request()))).build();
        try {
            var response=http.send(request,HttpResponse.BodyHandlers.ofString());
            if (response.statusCode()!=200) throw unavailable();
            Map<?,?> body=mapper.readValue(response.body(),Map.class);
            if (!BanditRepository.MODEL.equals(body.get("model_version")) || !(body.get("rankings") instanceof List<?> raw)
                    || raw.size()!=prepared.candidateIds().size()) throw unavailable();
            List<BanditRanking> rankings=new ArrayList<>(); Set<String> seen=new HashSet<>();
            double previous=Double.POSITIVE_INFINITY; String previousId="";
            for (Object item:raw) {
                if (!(item instanceof Map<?,?> row) || !(row.get("pitcher_id") instanceof String id)
                        || !(row.get("predicted_reward") instanceof Number score)
                        || !(row.get("model_rank") instanceof Number rank)) throw unavailable();
                double value=score.doubleValue(); int expected=rankings.size()+1;
                if (!prepared.candidateIds().contains(id) || !seen.add(id) || !Double.isFinite(value)
                        || rank.doubleValue()!=expected || value>previous
                        || (value==previous && id.compareTo(previousId)<0)) throw unavailable();
                rankings.add(new BanditRanking(Integer.parseInt(id),value,expected)); previous=value; previousId=id;
            }
            if (!seen.equals(prepared.candidateIds())) throw unavailable();
            return new BanditRecommendationResponse(BanditRepository.MODEL,List.copyOf(rankings));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); throw unavailable();
        } catch (IOException | RuntimeException e) {
            if (e instanceof BusinessException business) throw business;
            throw unavailable();
        }
    }

    private static BusinessException unavailable() { return new BusinessException(ErrorCode.MODEL_UNAVAILABLE); }
}
