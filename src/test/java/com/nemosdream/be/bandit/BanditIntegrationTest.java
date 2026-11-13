package com.nemosdream.be.bandit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nemosdream.be.bandit.client.BanditClient;
import com.nemosdream.be.bandit.dto.BanditPreparedRequest;
import com.nemosdream.be.bandit.repository.BanditRepository;
import com.nemosdream.be.bandit.service.BanditFeatureAssembler;
import com.nemosdream.be.bandit.service.BanditRecommendationService;
import com.nemosdream.be.global.error.BusinessException;
import com.nemosdream.be.session.domain.GameSession;
import com.nemosdream.be.session.domain.ActualGame;
import com.nemosdream.be.session.domain.Half;
import com.nemosdream.be.session.domain.Linescore;
import com.nemosdream.be.session.domain.PitcherDay;
import com.nemosdream.be.session.domain.SimState;
import com.nemosdream.be.session.domain.TeamSide;
import com.nemosdream.be.session.store.SessionStore;
import com.nemosdream.be.simulation.domain.Bases;
import java.nio.file.Path;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="B1_TEST_DB_URL",matches=".+")
@EnabledIfEnvironmentVariable(named="B1_TEST_AI_URL",matches=".+")
@EnabledIfEnvironmentVariable(named="B1_TEST_EXAMPLES",matches=".+")
class BanditIntegrationTest {
    private static final String GAME="20250322HHKT02025";
    @Autowired MockMvc mvc;
    @Autowired SessionStore store;
    @Autowired BanditRepository repository;
    @Autowired BanditFeatureAssembler assembler;
    @Autowired BanditRecommendationService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transaction;
    @Autowired ObjectMapper mapper;
    @LocalServerPort int port;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",()->System.getenv("B1_TEST_DB_URL"));
        registry.add("spring.datasource.username",()->"b1_test");
        registry.add("spring.datasource.password",()->"");
        registry.add("app.ai.base-url",()->System.getenv("B1_TEST_AI_URL"));
        registry.add("app.ai.timeout-ms",()->30000);
    }

    private SimState state(int inning, int outs, String bases, int awayScore, int homeScore,
                           int pitcher, Map<Integer,PitcherDay> used) {
        var away=new TeamSide(List.of(1,2,3,4,5,6,7,8,9),1,60002,0,0);
        var home=new TeamSide(List.of(11,12,13,14,15,16,17,18,19),1,pitcher,0,0);
        return new SimState(inning,Half.TOP,outs,Bases.of(bases),awayScore,homeScore,Linescore.empty(),
                away,home,used,Map.of(),null,null,false,List.of());
    }

    private GameSession session(String id, SimState state) {
        Set<Integer> bullpen=repository.roster(GAME).stream().filter(r->r.team().equals("KT"))
                .map(r->r.pitcherId()).collect(java.util.stream.Collectors.toSet());
        return new GameSession(id,GAME,LocalDate.of(2025,3,22),"KT","HH",false,"default",1L,Map.of(),
                new ActualGame("HH","KT",List.of(),List.of(),0,0,"actual"),bullpen,state,Instant.now());
    }

    @Test
    void realDatabaseAndFastApiMatchTwoFrozenDynamicExamples() throws Exception {
        var examples=mapper.readTree(Path.of(System.getenv("B1_TEST_EXAMPLES")).toFile());
        var s1=state(5,0,"0",2,3,54354,Map.of(54354,new PitcherDay(14,63,0,0,0)));
        var session=session("b1-example",s1); store.save(session);
        var prepared=assembler.assemble(session,s1);
        assertEquals(10,prepared.candidateIds().size());
        var game=(Map<?,?>)prepared.request().get("game_state");
        assertEquals(1,game.get("score_diff_defense"));
        assertEquals(2,game.get("n_pitchers_used"));
        assertEquals(0.9,(double)game.get("bullpen_available_ratio"),1e-12);
        assertEquals(1.2181665616612303,(double)game.get("leverage_index"),1e-12);
        assertBanditRankings(service.recommend(session.id()),examples.get("scenario_1_result"));
        var openApi=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+
                "/v3/api-docs")).GET().build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(200,openApi.statusCode());
        assertTrue(mapper.readTree(openApi.body()).get("paths").has("/api/v1/sessions/{sessionId}/recommend"));
        var s2=state(7,1,"13",2,4,69032,Map.of(54354,new PitcherDay(14,63,0,0,0),69032,new PitcherDay(1,4,0,0,0)));
        session.advance(s2);
        var next=assembler.assemble(session,s2);
        assertFalse(next.candidateIds().contains("69032")); assertFalse(next.candidateIds().contains("54354"));
        var g2=(Map<?,?>)next.request().get("game_state");
        assertEquals(3,g2.get("n_pitchers_used")); assertEquals(0.8,(double)g2.get("bullpen_available_ratio"),1e-12);
        assertEquals(2.5509010584342975,(double)g2.get("leverage_index"),1e-12);
        assertBanditRankings(service.recommend(session.id()),examples.get("scenario_2_result"));
    }

    @Test
    void directClientMatchesFrozenHttpGolden() {
        Path artifacts=Path.of(System.getenv("B1_TEST_EXAMPLES")).getParent().getParent().resolve("b1_final_serving_v3");
        Map<?,?> raw=mapper.readValue(artifacts.resolve("example_request.json").toFile(),Map.class);
        var game=(Map<String,Object>)raw.get("game_state");
        var candidates=(List<Map<String,Object>>)raw.get("candidates");
        var ids=candidates.stream().map(c->(String)c.get("pitcher_id")).collect(java.util.stream.Collectors.toSet());
        var prepared=new BanditPreparedRequest(Map.of("game_state",game,"candidates",candidates),Set.copyOf(ids));
        var actual=new BanditClient(mapper,System.getenv("B1_TEST_AI_URL"),30000).recommend(prepared);
        var expected=mapper.readTree(artifacts.resolve("example_output.json").toFile()).get("rankings");
        assertEquals(expected.size(),actual.rankings().size());
        for(int i=0;i<expected.size();i++) {
            assertEquals(Integer.parseInt(expected.get(i).get("pitcher_id").asText()),actual.rankings().get(i).pitcherId());
            assertEquals(expected.get(i).get("model_rank").asInt(),actual.rankings().get(i).modelRank());
            assertEquals(expected.get(i).get("predicted_reward").asDouble(),actual.rankings().get(i).predictedReward(),1e-12);
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named="B1_TEST_REAL_SESSION",matches="1")
    void createsSessionFromRealGamePrefixThenRecommendsOverHttp() throws Exception {
        var http=HttpClient.newHttpClient();
        String request="{\"game_id\":\""+GAME+"\",\"my_team\":\"KT\",\"start_inning\":5}";
        var start=http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/sessions"))
                .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(request)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(201,start.statusCode(),start.body());
        var state=mapper.readTree(start.body()).get("state");
        assertEquals(2,state.get("score").get("home").asInt());
        assertEquals(1,state.get("score").get("away").asInt());
        assertEquals(54354,state.get("current_pitcher").get("pitcher_id").asInt());
        assertEquals(50704,state.get("current_batter").get("batter_id").asInt());
        assertEquals(0.1,state.get("leverage_index").asDouble(),1e-6); // fixture 화면 LI
        String id=state.get("session_id").asText();
        var result=http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+
                "/api/v1/sessions/"+id+"/recommend")).POST(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200,result.statusCode(),result.body());
        var expected=mapper.readTree(Path.of(System.getenv("B1_TEST_EXAMPLES")).toFile()).get("scenario_1_result");
        assertCardRankings(mapper.readTree(result.body()),expected);
    }

    @Test
    @EnabledIfEnvironmentVariable(named="B1_TEST_REAL_SESSION",matches="1")
    void recommendedPitcherMovesThroughPaHalfAndUndoOverHttp() throws Exception {
        var start=postHttp("/api/v1/sessions","{\"game_id\":\""+GAME+"\",\"my_team\":\"KT\",\"start_inning\":5}");
        assertEquals(201,start.statusCode(),start.body());
        String id=mapper.readTree(start.body()).get("state").get("session_id").asText();
        String path="/api/v1/sessions/"+id;
        var initial=postHttp(path+"/recommend",null);
        assertEquals(200,initial.statusCode(),initial.body());
        var initialRankings=availableRankings(mapper.readTree(initial.body()));
        int pitcher=initialRankings.get(0).get("pitcher_id").asInt();
        var firstPa=postHttp(path+"/simulate-pa","{\"pitcher_id\":"+pitcher+"}");
        assertEquals(200,firstPa.statusCode(),firstPa.body());
        var state=mapper.readTree(firstPa.body()).get("result").get("next_state");
        assertEquals("defense",state.get("my_role").asText());
        var afterPa=postHttp(path+"/recommend",null);
        assertEquals(200,afterPa.statusCode(),afterPa.body());
        var afterRankings=availableRankings(mapper.readTree(afterPa.body()));
        for (int i=0;i<afterRankings.size();i++) assertNotEquals(pitcher,afterRankings.get(i).get("pitcher_id").asInt());
        var undo=postHttp(path+"/undo",null);
        assertEquals(200,undo.statusCode(),undo.body());
        var restored=postHttp(path+"/recommend",null);
        assertEquals(200,restored.statusCode(),restored.body());
        assertEquals(initialRankings,availableRankings(mapper.readTree(restored.body())));
        String role="defense";
        for (int i=0;i<50 && role.equals("defense");i++) {
            var pa=postHttp(path+"/simulate-pa","{\"pitcher_id\":"+pitcher+"}");
            assertEquals(200,pa.statusCode(),pa.body());
            role=mapper.readTree(pa.body()).get("result").get("next_state").get("my_role").asText();
        }
        assertEquals("offense",role);
        assertEquals(400,postHttp(path+"/recommend",null).statusCode());
        var half=postHttp(path+"/advance-half",null);
        assertEquals(200,half.statusCode(),half.body());
        assertEquals("defense",mapper.readTree(half.body()).get("state").get("my_role").asText());
        var next=postHttp(path+"/recommend",null);
        assertEquals(200,next.statusCode(),next.body());
        var nextRankings=availableRankings(mapper.readTree(next.body()));
        for (int i=0;i<nextRankings.size();i++) assertNotEquals(pitcher,nextRankings.get(i).get("pitcher_id").asInt());
    }

    private HttpResponse<String> postHttp(String path, String body) throws Exception {
        var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path));
        if (body!=null) builder.header("Content-Type","application/json");
        return HttpClient.newHttpClient().send(builder.POST(body==null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }

    private void assertBanditRankings(com.nemosdream.be.bandit.dto.BanditRecommendationResponse response,
                                     tools.jackson.databind.JsonNode expected) {
        assertEquals(BanditRepository.MODEL,response.modelVersion());
        var rankings=response.rankings(); assertEquals(expected.size(),rankings.size());
        for (int i=0;i<expected.size();i++) {
            assertEquals(expected.get(i).get("pitcher_id").asInt(),rankings.get(i).pitcherId());
            assertEquals(expected.get(i).get("model_rank").asInt(),rankings.get(i).modelRank());
            assertEquals(expected.get(i).get("predicted_reward").asDouble(),rankings.get(i).predictedReward(),1e-12);
        }
    }

    private List<tools.jackson.databind.JsonNode> availableRankings(tools.jackson.databind.JsonNode response) {
        assertEquals("live",response.get("source").asText());
        List<tools.jackson.databind.JsonNode> rows=new java.util.ArrayList<>();
        for (var candidate:response.get("candidates")) {
            var pitcher=candidate.get("pitcher");
            if ("available".equals(pitcher.get("availability").asText())) rows.add(pitcher);
            else assertTrue(pitcher.get("model_score").isNull());
        }
        return rows;
    }

    private void assertCardRankings(tools.jackson.databind.JsonNode response, tools.jackson.databind.JsonNode expected) {
        var rows=availableRankings(response); assertEquals(expected.size(),rows.size());
        for (int i=0;i<expected.size();i++) {
            assertEquals(expected.get(i).get("pitcher_id").asInt(),rows.get(i).get("pitcher_id").asInt());
            assertEquals(expected.get(i).get("model_rank").asInt(),rows.get(i).get("model_rank").asInt());
            assertEquals(expected.get(i).get("predicted_reward").asDouble(),rows.get(i).get("model_score").asDouble(),1e-12);
        }
    }

    @Test
    void datasetAndLookupValidationFailClosed() {
        repository.verifyDataset(GAME);
        assertEquals(18155,jdbc.queryForObject("select count(*) from bandit_pitcher_snapshots where version=?",Integer.class,BanditRepository.VERSION));
        assertEquals(3888,jdbc.queryForObject("select count(*) from bandit_train_li_lookup where version=?",Integer.class,BanditRepository.VERSION));
        assertEquals(repository.trainLi(GAME,5,"top",0,"0",4),repository.trainLi(GAME,5,"top",0,"0",100));
        transaction.executeWithoutResult(status->{
            jdbc.update("delete from bandit_train_li_lookup where version=? and inning=1 and half='top' and out_count=0 and base_state='0' and score_diff_attacker=0",BanditRepository.VERSION);
            assertThrows(BusinessException.class,()->repository.verifyDataset(GAME));
            status.setRollbackOnly();
        });
        repository.verifyDataset(GAME);
        assertEquals("unchanged",jdbc.queryForObject("select value from b1_test_existing_data",String.class));
    }

    @Test
    void exhaustedBullpenReturnsExistingConflictContract() throws Exception {
        Map<Integer,PitcherDay> used=new HashMap<>();
        used.put(54354,new PitcherDay(14,63,0,0,0));
        for (var snapshot:repository.snapshots(GAME,"KT")) used.put(snapshot.pitcherId(),new PitcherDay(1,4,0,0,0));
        var s=state(7,1,"13",2,4,69032,used);
        var session=session("b1-exhausted",s); store.save(session);
        mvc.perform(post("/api/v1/sessions/{id}/recommend",session.id()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("NO_AVAILABLE_PITCHER"));
    }

    @Test
    void offenseAndGameOverRejectRecommendation() throws Exception {
        var base=state(5,0,"0",2,3,54354,Map.of(54354,new PitcherDay(14,63,0,0,0)));
        var offense=new SimState(5,Half.BOT,0,Bases.EMPTY,2,3,base.linescore(),base.away(),base.home(),
                base.pitcherDays(),base.batterDays(),null,null,false,List.of());
        store.save(session("b1-offense",offense));
        mvc.perform(post("/api/v1/sessions/{id}/recommend","b1-offense"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_ROLE"));
        var ended=new SimState(9,Half.TOP,0,Bases.EMPTY,2,3,base.linescore(),base.away(),base.home(),
                base.pitcherDays(),base.batterDays(),null,null,true,List.of());
        store.save(session("b1-ended",ended));
        mvc.perform(post("/api/v1/sessions/{id}/recommend","b1-ended"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }
}
