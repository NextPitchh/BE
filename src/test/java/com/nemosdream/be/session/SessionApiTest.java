package com.nemosdream.be.session;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;

import com.nemosdream.be.support.PostgresTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@Sql(scripts = {
        "file:etl/schema/01_teams_players.sql",
        "file:etl/schema/02_games_pa.sql",
        "file:etl/schema/03_bullpen_lineup.sql",
        "file:etl/schema/04_we_li_table.sql",
        "file:etl/schema/06_pitcher_game_stats.sql",
        "file:etl/schema/07_batter_game_stats.sql",
        "file:etl/schema/08_matchups.sql",
        "classpath:sql/fixture-session.sql",
        "classpath:sql/fixture-we-li.sql"
})
@Sql(statements = "DROP TABLE IF EXISTS matchups, batter_game_stats, pitcher_game_stats, we_li_table, game_bullpen, "
        + "game_batting_order, game_pa, games, players, teams CASCADE",
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class SessionApiTest extends PostgresTestSupport {

    @Autowired
    MockMvc mockMvc;

    @Test
    void 시작_이닝의_내_팀_수비_첫_타석_직전_상태로_시작한다() throws Exception {
        create("""
                {"game_id": "20250601HHKT02025", "my_team": "KT", "start_inning": 5}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.state.session_id").value(startsWith("s_")))
                .andExpect(jsonPath("$.state.inning").value(5))
                .andExpect(jsonPath("$.state.half").value("top"))
                .andExpect(jsonPath("$.state.out_count").value(0))
                .andExpect(jsonPath("$.state.base_state").value("0"))
                .andExpect(jsonPath("$.state.score.away").value(4))
                .andExpect(jsonPath("$.state.score.home").value(0))
                .andExpect(jsonPath("$.state.score_diff").value(-4))
                .andExpect(jsonPath("$.state.score_diff_attacker").value(4))
                .andExpect(jsonPath("$.state.my_team").value("KT"))
                .andExpect(jsonPath("$.state.opponent_team").value("HH"))
                .andExpect(jsonPath("$.state.my_role").value("defense"))
                // 승률은 내 팀(수비) 관점 = 1 - 공격팀 WE(0.9387)
                .andExpect(jsonPath("$.state.win_expectancy").value(closeTo(0.0613, 1e-4)))
                .andExpect(jsonPath("$.state.leverage_index").value(closeTo(0.159, 1e-3)))
                .andExpect(jsonPath("$.state.we_perspective").value("KT"))
                .andExpect(jsonPath("$.state.is_diverged").value(false))
                .andExpect(jsonPath("$.state.diverged_at").value(nullValue()))
                .andExpect(jsonPath("$.state.is_game_over").value(false))
                .andExpect(jsonPath("$.state.can_undo").value(false));
    }

    @Test
    void 현재_투수는_직전_수비_반이닝을_마친_투수다() throws Exception {
        // 실제 감독은 5회초 시작에 60003 으로 바꿨지만, 그 결정은 사용자가 한다
        create("""
                {"game_id": "20250601HHKT02025", "my_team": "KT", "start_inning": 5}""")
                .andExpect(jsonPath("$.state.current_pitcher.pitcher_id").value(60002))
                .andExpect(jsonPath("$.state.current_pitcher.throws").value("R"))
                .andExpect(jsonPath("$.state.current_pitcher.availability").value("current"))
                .andExpect(jsonPath("$.state.current_pitcher.workload.pa_faced_today").value(1))
                .andExpect(jsonPath("$.state.current_pitcher.workload.est_pitch_count").value(6))
                .andExpect(jsonPath("$.state.current_pitcher.season").value(nullValue()));
    }

    @Test
    void 타순과_오늘_기록과_스코어보드() throws Exception {
        create("""
                {"game_id": "20250601HHKT02025", "my_team": "KT", "start_inning": 5}""")
                .andExpect(jsonPath("$.state.current_batter.batter_id").value(51004))
                .andExpect(jsonPath("$.state.current_batter.order").value(4))
                .andExpect(jsonPath("$.state.current_batter.position").value("DH"))
                .andExpect(jsonPath("$.state.current_batter.vs_current_pitcher").value(nullValue()))
                // 5번 자리 대타는 시작 뒤에 나왔으므로 선발 51005 가 다음 타자
                .andExpect(jsonPath("$.state.upcoming_batters[*].batter_id").value(contains(51005, 51006, 51007)))
                .andExpect(jsonPath("$.state.upcoming_batters[0].today.log").isEmpty())
                .andExpect(jsonPath("$.state.linescore.away[0]").value(1))
                .andExpect(jsonPath("$.state.linescore.away[1]").value(nullValue()))
                .andExpect(jsonPath("$.state.linescore.away[3]").value(3))
                .andExpect(jsonPath("$.state.linescore.away[4]").value(0))
                .andExpect(jsonPath("$.state.linescore.away[5]").value(nullValue()))
                .andExpect(jsonPath("$.state.linescore.home[3]").value(0))
                .andExpect(jsonPath("$.state.linescore.totals.away.R").value(4))
                .andExpect(jsonPath("$.state.linescore.totals.away.H").value(2))
                .andExpect(jsonPath("$.state.linescore.totals.away.E").value(nullValue()))
                .andExpect(jsonPath("$.state.linescore.totals.home.H").value(1))
                .andExpect(jsonPath("$.state.linescore.totals.home.B").value(1));
    }

    @Test
    void 시작할_수_없는_요청() throws Exception {
        create("""
                {"game_id": "20250602HHKT02025", "my_team": "KT", "start_inning": 5}""")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("GAME_NOT_FOUND"));
        create("""
                {"game_id": "20250601HHKT02025", "my_team": "LG", "start_inning": 5}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
        create("""
                {"game_id": "20250601HHKT02025", "my_team": "KT", "start_inning": 4}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("start_inning: start_inning 은 5~9 입니다."));
    }

    private ResultActions create(String body) throws Exception {
        return mockMvc.perform(post("/api/v1/sessions").contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
