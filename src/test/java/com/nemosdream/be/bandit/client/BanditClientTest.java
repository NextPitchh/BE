package com.nemosdream.be.bandit.client;

import static org.junit.jupiter.api.Assertions.*;

import com.nemosdream.be.bandit.dto.BanditPreparedRequest;
import com.nemosdream.be.bandit.repository.BanditRepository;
import com.nemosdream.be.global.error.BusinessException;
import com.nemosdream.be.global.error.ErrorCode;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class BanditClientTest {
    private static final String VERSION=BanditRepository.MODEL;
    private final BanditPreparedRequest request=new BanditPreparedRequest(Map.of("candidates",java.util.List.of()),Set.of("1","2"));
    private String body(String rows) { return "{\"model_version\":\""+VERSION+"\",\"rankings\":"+rows+"}"; }

    @Test
    void validatesRankingsAndHttpFailures() throws Exception {
        var data=new AtomicReference<>(body("[{\"pitcher_id\":\"1\",\"predicted_reward\":0.2,\"model_rank\":1},{\"pitcher_id\":\"2\",\"predicted_reward\":0.1,\"model_rank\":2}]"));
        var status=new AtomicInteger(200);
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/v1/bandit/recommend",exchange->{
            try {
                if (status.get()==201) Thread.sleep(1500);
                byte[] bytes=data.get().getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(status.get(),bytes.length); exchange.getResponseBody().write(bytes);
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            catch (java.io.IOException ignored) { }
            finally { exchange.close(); }
        });
        server.start();
        try {
            var client=new BanditClient(new ObjectMapper(),"http://127.0.0.1:"+server.getAddress().getPort(),300);
            assertEquals(2,client.recommend(request).rankings().size());
            String valid=data.get();
            for (String invalid:new String[]{valid.replace(VERSION,"other"),valid.replace("\"2\"","\"9\""),
                    valid.replace("\"2\"","\"1\""),valid.replace("\"model_rank\":2","\"model_rank\":3"),
                    valid.replace("0.2","1e999"),valid.replace("0.2","0.0")}) {
                data.set(invalid);
                assertEquals(ErrorCode.MODEL_UNAVAILABLE,assertThrows(BusinessException.class,()->client.recommend(request)).errorCode());
            }
            data.set(valid); status.set(503);
            assertEquals(ErrorCode.MODEL_UNAVAILABLE,assertThrows(BusinessException.class,()->client.recommend(request)).errorCode());
            status.set(201);
            assertEquals(ErrorCode.MODEL_UNAVAILABLE,assertThrows(BusinessException.class,()->client.recommend(request)).errorCode());
        } finally { server.stop(0); }
    }
}
