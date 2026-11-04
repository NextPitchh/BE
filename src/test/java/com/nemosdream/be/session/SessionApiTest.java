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
        "file:etl/schema/09_outcome_counts.sql",
        "file:etl/schema/05_li_percentile.sql",
        "classpath:sql/fixture-session.sql",
        "classpath:sql/fixture-we-li.sql",
        "classpath:sql/fixture-li-percentile.sql"
})
@Sql(statements = "DROP TABLE IF EXISTS li_percentile, outcome_counts, league_outcome_rates, matchups, batter_game_stats, "
        + "pitcher_game_stats, we_li_table, game_bullpen, "
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

    @Test
    void 실제_감독과_같은_투수면_실제_기록을_재생한다() throws Exception {
        String sid = sessionId();
        simulate(sid, 60003)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.source").value("actual"))
                .andExpect(jsonPath("$.result.outcome").value("FO"))
                .andExpect(jsonPath("$.result.outcome_label").value("플라이 아웃"))
                .andExpect(jsonPath("$.result.runs_scored").value(0))
                .andExpect(jsonPath("$.result.probs.SO").exists())
                .andExpect(jsonPath("$.result.probs.GDP").value(0.0))                // 주자 없음
                // 공격팀(HH) WE 0.9387 → 0.955
                .andExpect(jsonPath("$.result.delta_we_attacker").value(closeTo(0.0163, 1e-4)))
                .andExpect(jsonPath("$.result.delta_we_defender").value(closeTo(-0.0163, 1e-4)))
                .andExpect(jsonPath("$.result.next_state.out_count").value(1))
                .andExpect(jsonPath("$.result.next_state.is_diverged").value(false))
                .andExpect(jsonPath("$.result.next_state.current_pitcher.pitcher_id").value(60003))
                .andExpect(jsonPath("$.result.next_state.current_pitcher.workload.est_pitch_count").value(4))
                // 다음 실제 타석에 대타(51009)가 나왔으므로 재생 후에는 대타가 현재 타자
                .andExpect(jsonPath("$.result.next_state.current_batter.batter_id").value(51009))
                .andExpect(jsonPath("$.result.next_state.current_batter.order").value(5))
                .andExpect(jsonPath("$.result.next_state.can_undo").value(true));
    }

    @Test
    void 실제와_다른_투수를_고르면_분기하고_주사위로_진행한다() throws Exception {
        String sid = sessionId();
        // 실제 감독은 60003 으로 바꿨는데 현재 투수 60002 를 유지
        simulate(sid, 60002)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.source").value("simulated"))
                .andExpect(jsonPath("$.result.next_state.is_diverged").value(true))
                .andExpect(jsonPath("$.result.next_state.diverged_at.inning").value(5))
                .andExpect(jsonPath("$.result.next_state.diverged_at.half").value("top"))
                .andExpect(jsonPath("$.result.next_state.diverged_at.pa_index").value(6))
                .andExpect(jsonPath("$.result.next_state.current_pitcher.workload.pa_faced_today").value(2));
        // 분기 후에는 계속 주사위, 다른 불펜 투수로 교체 가능
        simulate(sid, 60004)
                .andExpect(jsonPath("$.result.source").value("simulated"))
                .andExpect(jsonPath("$.result.next_state.diverged_at.pa_index").value(6));
    }

    @Test
    void 나올_수_없는_투수는_거절한다() throws Exception {
        String sid = sessionId();
        simulate(sid, 60001)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("이미 등판한 투수는 다시 나올 수 없습니다: 60001"));
        simulate(sid, 61001)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
        mockMvc.perform(post("/api/v1/sessions/s_없음/simulate-pa").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pitcher_id\": 60003}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SESSION_EXPIRED"));
    }

    @Test
    void 분기_전_공격_반이닝은_실제_기록을_재생한다() throws Exception {
        String sid = sessionId();
        simulate(sid, 60003);
        simulate(sid, 60003);
        simulate(sid, 60003)                       // 3아웃 → 내 팀 공격
                .andExpect(jsonPath("$.result.next_state.half").value("bot"))
                .andExpect(jsonPath("$.result.next_state.my_role").value("offense"));
        // 공격 중에는 투수를 고를 수 없다
        simulate(sid, 60003).andExpect(jsonPath("$.error.code").value("INVALID_ROLE"));

        advance(sid)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.source").value("actual"))
                .andExpect(jsonPath("$.summary.runs").value(2))
                .andExpect(jsonPath("$.summary.hits").value(2))
                .andExpect(jsonPath("$.summary.pa_log[*].batter_id").value(contains(52004, 52005, 52006, 52007, 52008)))
                .andExpect(jsonPath("$.summary.pa_log[*].outcome").value(contains("1B", "HR", "SO", "GO", "FO")))
                .andExpect(jsonPath("$.summary.pa_log[1].description").value("좌월 2점 홈런"))
                .andExpect(jsonPath("$.summary.we_trace.length()").value(6))
                .andExpect(jsonPath("$.state.inning").value(6))
                .andExpect(jsonPath("$.state.half").value("top"))
                .andExpect(jsonPath("$.state.my_role").value("defense"))
                .andExpect(jsonPath("$.state.score.home").value(2))
                .andExpect(jsonPath("$.state.linescore.home[4]").value(2))
                .andExpect(jsonPath("$.state.linescore.away[5]").value(0))
                .andExpect(jsonPath("$.state.current_batter.batter_id").value(51007))
                .andExpect(jsonPath("$.state.is_diverged").value(false));
        // 수비 중에는 자동 진행할 수 없다
        advance(sid).andExpect(jsonPath("$.error.code").value("INVALID_ROLE"));
    }

    @Test
    void 분기_후_공격_반이닝은_주사위로_진행한다() throws Exception {
        String sid = sessionId();
        String role = "defense";
        for (int i = 0; i < 40 && role.equals("defense"); i++) {
            String body = simulate(sid, 60002).andReturn().getResponse().getContentAsString();
            role = com.jayway.jsonpath.JsonPath.read(body, "$.result.next_state.my_role");
        }
        advance(sid)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.source").value("simulated"))
                .andExpect(jsonPath("$.state.is_diverged").value(true));
    }

    @Test
    void 되돌리기는_직전_상태로_돌아가고_분기도_되돌린다() throws Exception {
        String sid = sessionId();
        undo(sid)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CANNOT_UNDO"));

        simulate(sid, 60002).andExpect(jsonPath("$.result.next_state.is_diverged").value(true));
        undo(sid)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state.is_diverged").value(false))
                .andExpect(jsonPath("$.state.diverged_at").value(nullValue()))
                .andExpect(jsonPath("$.state.out_count").value(0))
                .andExpect(jsonPath("$.state.current_pitcher.pitcher_id").value(60002))
                .andExpect(jsonPath("$.state.current_pitcher.workload.pa_faced_today").value(1))
                .andExpect(jsonPath("$.state.can_undo").value(false));
    }

    @Test
    void 되돌린_뒤_같은_선택이면_같은_결과() throws Exception {
        String sid = sessionId();
        String first = outcome(simulate(sid, 60004));
        undo(sid);
        org.assertj.core.api.Assertions.assertThat(outcome(simulate(sid, 60004))).isEqualTo(first);
    }

    @Test
    void 승률_추이는_반이닝_단위로_실제와_시뮬레이션을_나눈다() throws Exception {
        String sid = sessionId();
        weTrace(sid)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perspective").value("KT"))
                // 실제 기록이 있는 반이닝: 1초, 1말, 4초, 4말, 5초, 5말, 6초
                .andExpect(jsonPath("$.actual.length()").value(7))
                .andExpect(jsonPath("$.actual[4].inning").value(5))
                .andExpect(jsonPath("$.actual[4].half").value("top"))
                .andExpect(jsonPath("$.actual[6].we").value(0.0))          // 실제 경기는 KT 패배로 끝
                .andExpect(jsonPath("$.simulated").isEmpty())
                .andExpect(jsonPath("$.diverge_at").value(nullValue()))
                .andExpect(jsonPath("$.current").value(closeTo(0.0613, 1e-4)));

        simulate(sid, 60002);
        weTrace(sid)
                .andExpect(jsonPath("$.diverge_at.inning").value(5))
                .andExpect(jsonPath("$.diverge_at.half").value("top"))
                .andExpect(jsonPath("$.simulated[0].inning").value(5))
                .andExpect(jsonPath("$.simulated[0].half").value("top"))
                .andExpect(jsonPath("$.simulated[0].leverage_index").value(closeTo(0.159, 1e-3)));
    }

    @Test
    void 결과_화면은_실제와_같은_선택이면_실제_경기와_같다() throws Exception {
        String sid = sessionId();
        simulate(sid, 60003);                      // 실제 감독 교체와 같은 선택
        simulate(sid, 60003);
        simulate(sid, 60003);                      // 5회초 종료
        advance(sid);                              // 5회말 2점

        result(sid)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.simulated.score.my").value(2))
                .andExpect(jsonPath("$.simulated.score.opp").value(4))
                .andExpect(jsonPath("$.simulated.runs_allowed_in_range").value(0))
                .andExpect(jsonPath("$.simulated.result").value("in_progress"))
                .andExpect(jsonPath("$.actual.score.my").value(2))       // games.final_home
                .andExpect(jsonPath("$.actual.score.opp").value(5))
                .andExpect(jsonPath("$.actual.result").value("loss"))
                .andExpect(jsonPath("$.actual.final_we").value(0.0))
                .andExpect(jsonPath("$.pitcher_usage.length()").value(1))
                .andExpect(jsonPath("$.pitcher_usage[0].pitcher_id").value(60003))
                .andExpect(jsonPath("$.pitcher_usage[0].from.pa").value(0))
                .andExpect(jsonPath("$.pitcher_usage[0].to.pa").value(2))
                .andExpect(jsonPath("$.pitcher_usage[0].pitches_est").value(11))
                .andExpect(jsonPath("$.actual_pitcher_usage[0].pitcher_id").value(60003))
                .andExpect(jsonPath("$.decisions.length()").value(3))
                .andExpect(jsonPath("$.decisions[0].user_choice.pitcher_id").value(60003))
                .andExpect(jsonPath("$.decisions[0].actual_choice.pitcher_id").value(60003))
                .andExpect(jsonPath("$.decisions[0].is_change").value(true))
                .andExpect(jsonPath("$.decisions[1].is_change").value(false))
                .andExpect(jsonPath("$.decisions[0].outcome").value("FO"))
                .andExpect(jsonPath("$.decisions[0].model_top").value(nullValue()))
                .andExpect(jsonPath("$.agreement.total_decisions").value(3))
                .andExpect(jsonPath("$.agreement.same_as_actual").value(3))
                .andExpect(jsonPath("$.agreement.mine_in_top3").value(nullValue()))
                .andExpect(jsonPath("$.bullpen_used.mine").value(3))      // 60001, 60002, 60003
                .andExpect(jsonPath("$.bullpen_used.total").value(4));
    }

    @Test
    void 분기_후_결정은_실제_선택이_없다() throws Exception {
        String sid = sessionId();
        simulate(sid, 60004);
        simulate(sid, 60004);
        result(sid)
                .andExpect(jsonPath("$.decisions[0].actual_choice.pitcher_id").value(60003))
                .andExpect(jsonPath("$.decisions[1].actual_choice").value(nullValue()))
                .andExpect(jsonPath("$.agreement.same_as_actual").value(0))
                .andExpect(jsonPath("$.pitcher_usage[0].pitcher_id").value(60004));
    }

    private ResultActions result(String sessionId) throws Exception {
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/api/v1/sessions/" + sessionId + "/result"));
    }

    @Test
    void 추천은_현재_투수를_분리하고_불펜_명단을_후보로_준다() throws Exception {
        String sid = sessionId();
        recommend(sid)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leverage_index").value(closeTo(0.159, 1e-3)))
                .andExpect(jsonPath("$.leverage_band").value("저"))
                .andExpect(jsonPath("$.li_percentile").value(1))          // fixture-li-percentile: 0.16 → 16 / 10
                .andExpect(jsonPath("$.li_max").value(5.0))
                .andExpect(jsonPath("$.source").value("mock"))
                .andExpect(jsonPath("$.current_pitcher.pitcher.pitcher_id").value(60002))
                .andExpect(jsonPath("$.current_pitcher.this_outing.pitches").value(6))
                // 60002 의 타석(4회초)이 그 반이닝 마지막 실제 타석 → 남은 아웃 3개를 그 투수가 잡은 것으로 센다
                .andExpect(jsonPath("$.current_pitcher.this_outing.outs").value(3))
                .andExpect(jsonPath("$.current_pitcher.this_outing.hits_allowed").value(1))
                .andExpect(jsonPath("$.current_pitcher.pitcher.model_score").doesNotExist())
                // 선발 60001 은 경기 전 불펜 명단이 아니라 후보가 아니다
                .andExpect(jsonPath("$.candidates[*].pitcher.pitcher_id").value(
                        org.hamcrest.Matchers.containsInAnyOrder(60003, 60004)))
                .andExpect(jsonPath("$.candidates[0].pitcher.availability").value("available"))
                .andExpect(jsonPath("$.candidates[0].pitcher.model_score").value(nullValue()))
                .andExpect(jsonPath("$.candidates[0].pitcher.model_rank").value(nullValue()))
                .andExpect(jsonPath("$.candidates[0].pitcher.role").value("bullpen"))
                .andExpect(jsonPath("$.bullpen_summary.available").value(2))
                .andExpect(jsonPath("$.bullpen_summary.used").value(0))
                .andExpect(jsonPath("$.bullpen_source").value("actual"))
                .andExpect(jsonPath("$.actual_decision.pitcher_id").value(60003))
                .andExpect(jsonPath("$.actual_decision.was_change").value(true));
    }

    @Test
    void 등판한_투수는_used_로_뒤에_남고_점수가_없다() throws Exception {
        String sid = sessionId();
        simulate(sid, 60003);                       // 실제 재생: 60002 → 60003 교체
        recommend(sid)
                .andExpect(jsonPath("$.current_pitcher.pitcher.pitcher_id").value(60003))
                .andExpect(jsonPath("$.current_pitcher.this_outing.pitches").value(4))
                .andExpect(jsonPath("$.current_pitcher.this_outing.outs").value(1))
                .andExpect(jsonPath("$.candidates[*].pitcher.pitcher_id").value(contains(60004, 60002)))
                .andExpect(jsonPath("$.candidates[1].pitcher.availability").value("used"))
                .andExpect(jsonPath("$.candidates[1].pitcher.unavailable_reason").value("이미 등판"))
                .andExpect(jsonPath("$.candidates[1].pitcher.model_score").value(nullValue()))
                .andExpect(jsonPath("$.bullpen_summary.used").value(1));
    }

    @Test
    void 불펜을_다_쓰면_NO_AVAILABLE_PITCHER_분기_후에는_실제_선택이_없다() throws Exception {
        String sid = sessionId();
        simulate(sid, 60004);                       // 분기
        recommend(sid).andExpect(jsonPath("$.actual_decision").value(nullValue()));
        simulate(sid, 60003);                       // 0아웃 시작이라 2타석으로는 3아웃이 안 된다
        recommend(sid)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("NO_AVAILABLE_PITCHER"));
    }

    @Test
    void 공격_중에는_추천하지_않는다() throws Exception {
        String sid = sessionId();
        simulate(sid, 60003);
        simulate(sid, 60003);
        simulate(sid, 60003);
        recommend(sid)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_ROLE"));
    }

    private ResultActions recommend(String sessionId) throws Exception {
        return mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/recommend"));
    }

    @Test
    void 같은_선택으로_반복은_재현되고_세션을_바꾸지_않는다() throws Exception {
        String sid = sessionId();
        simulate(sid, 60004);
        String before = result(sid).andReturn().getResponse().getContentAsString();

        String first = repeat(sid, "{\"n\": 30}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.n").value(30))
                .andExpect(jsonPath("$.better_than_actual_rate").exists())
                .andExpect(jsonPath("$.final_we.median").exists())
                .andExpect(jsonPath("$.actual.runs_allowed").value(1))      // 5회초 시작 HH 4점 → 최종 5점
                .andReturn().getResponse().getContentAsString();
        String second = repeat(sid, "{\"n\": 30}").andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(withoutElapsed(second)).isEqualTo(withoutElapsed(first));
        int total = com.jayway.jsonpath.JsonPath.<java.util.Map<String, Integer>>read(first, "$.runs_allowed.histogram")
                .values().stream().mapToInt(Integer::intValue).sum();
        org.assertj.core.api.Assertions.assertThat(total).isEqualTo(30);
        org.assertj.core.api.Assertions.assertThat(result(sid).andReturn().getResponse().getContentAsString())
                .isEqualTo(before);
    }

    @Test
    void 반복_횟수는_1에서_500() throws Exception {
        String sid = sessionId();
        repeat(sid, "{\"n\": 0}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("n: n 은 1~500 입니다."));
        mockMvc.perform(post("/api/v1/sessions/" + sid + "/repeat"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.n").value(100));
    }

    private ResultActions repeat(String sessionId, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/repeat")
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String withoutElapsed(String json) {
        return json.replaceAll("\"elapsed_ms\":\\d+", "");
    }

    private String outcome(ResultActions result) throws Exception {
        return com.jayway.jsonpath.JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.result.outcome");
    }

    private ResultActions undo(String sessionId) throws Exception {
        return mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/undo"));
    }

    private ResultActions weTrace(String sessionId) throws Exception {
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/api/v1/sessions/" + sessionId + "/we-trace"));
    }

    private ResultActions advance(String sessionId) throws Exception {
        return mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/advance-half"));
    }

    private String sessionId() throws Exception {
        String body = create("""
                {"game_id": "20250601HHKT02025", "my_team": "KT", "start_inning": 5}""")
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(body, "$.state.session_id");
    }

    private ResultActions simulate(String sessionId, int pitcherId) throws Exception {
        return mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/simulate-pa")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pitcher_id\": " + pitcherId + "}"));
    }

    private ResultActions create(String body) throws Exception {
        return mockMvc.perform(post("/api/v1/sessions").contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
