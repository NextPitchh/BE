package com.nemosdream.be;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nemosdream.be.support.PostgresTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

/** /teams, /games, /config 를 실제 스키마(etl/schema)와 PostgreSQL 로 확인한다. */
@SpringBootTest
@AutoConfigureMockMvc
@Sql(scripts = {
        "file:etl/schema/01_teams_players.sql",
        "file:etl/schema/02_games_pa.sql",
        "classpath:sql/fixture-games.sql"
})
@Sql(statements = "DROP TABLE IF EXISTS game_pa, games, players, teams CASCADE",
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class ApiIntegrationTest extends PostgresTestSupport {

    @Autowired
    MockMvc mvc;

    @Test
    void 팀_목록() throws Exception {
        mvc.perform(get("/api/v1/teams"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[*].code", contains("HH", "KT", "LG")))
                .andExpect(jsonPath("$[0].name").value("한화"))
                .andExpect(jsonPath("$[0].logo_url").value(nullValue()));
    }

    @Test
    void 경기_목록은_내_팀_수비_이닝_기준() throws Exception {
        mvc.perform(get("/api/v1/games").param("team", "HH"))
                .andExpect(status().isOk())
                // 콜드게임, 다른 시즌, HH 안 뛴 경기는 빠진다
                .andExpect(jsonPath("$[*].game_id", contains("20250401HHKT02025", "20250402LGHH02025")))
                .andExpect(jsonPath("$[0].date").value("2025-04-01"))
                .andExpect(jsonPath("$[0].away").value("HH"))
                .andExpect(jsonPath("$[0].final_score.away").value(6))
                .andExpect(jsonPath("$[0].final_score.home").value(9))
                // HH 가 원정 → away 배열
                .andExpect(jsonPath("$[0].available_innings", contains(5, 6, 7, 8)))
                .andExpect(jsonPath("$[0].high_leverage_innings", contains(7)))
                // HH 가 홈 → home 배열
                .andExpect(jsonPath("$[1].high_leverage_innings", contains(6, 9)));
    }

    @Test
    void 상대_팀과_시즌_필터() throws Exception {
        mvc.perform(get("/api/v1/games").param("team", "HH").param("opponent", "KT"))
                .andExpect(jsonPath("$[*].game_id", contains("20250401HHKT02025")));

        mvc.perform(get("/api/v1/games").param("team", "HH").param("season", "2024"))
                .andExpect(jsonPath("$[*].game_id", contains("20240501HHKT02024")));
    }

    @Test
    void 잘못된_팀_코드는_400() throws Exception {
        mvc.perform(get("/api/v1/games").param("team", "XX"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        mvc.perform(get("/api/v1/games").param("team", "hanwha"))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/v1/games").param("team", "HH").param("opponent", "HH"))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/v1/games"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("team: 필수 값입니다."));
    }

    @Test
    void 앱_설정() throws Exception {
        mvc.perform(get("/api/v1/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leverage.league_avg").value(1.0))
                .andExpect(jsonPath("$.leverage.max").value(5.0))
                .andExpect(jsonPath("$.leverage.bands", hasSize(4)))
                .andExpect(jsonPath("$.leverage.bands[3].label").value("매우 높음"))
                .andExpect(jsonPath("$.model.score_unit").value("wpa_per_pa"))
                .andExpect(jsonPath("$.model.provisional").value(true))
                .andExpect(jsonPath("$.li_source_note").exists())
                .andExpect(jsonPath("$.lambda").doesNotExist());
    }
}
