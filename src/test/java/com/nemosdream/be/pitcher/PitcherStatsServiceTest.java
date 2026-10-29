package com.nemosdream.be.pitcher;

import static org.assertj.core.api.Assertions.assertThat;

import com.nemosdream.be.pitcher.dto.HandSplit;
import com.nemosdream.be.pitcher.dto.PitcherCardStats;
import com.nemosdream.be.pitcher.dto.SeasonStats;
import com.nemosdream.be.pitcher.service.PitcherStatsService;
import com.nemosdream.be.support.PostgresTestSupport;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@Sql(scripts = {
        "file:etl/schema/01_teams_players.sql",
        "file:etl/schema/02_games_pa.sql",
        "file:etl/schema/06_pitcher_game_stats.sql",
        "classpath:sql/fixture-pitcher-stats.sql"
})
@Sql(statements = "DROP TABLE IF EXISTS pitcher_game_stats, game_pa, games, players, teams CASCADE",
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class PitcherStatsServiceTest extends PostgresTestSupport {

    @Autowired
    PitcherStatsService service;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void 경기_전_카드_기록을_명세_형태로_만든다() {
        PitcherCardStats s = service.getCardStats("20250601HHKT02025", List.of(60841)).get(60841);

        assertThat(s.season()).isEqualTo(new SeasonStats(null, 1.18, 52.1, 61, 24, 55));
        assertThat(s.vsBatterHand().left()).isEqualTo(new HandSplit(0.241, 0.283, 0.098, 118));
        assertThat(s.fatigue().daysRest()).isEqualTo(2);
        assertThat(s.fatigue().pitchesLast3d()).isEqualTo(35);
        assertThat(s.qualityPercentile()).isEqualTo(84);
    }

    @Test
    void 기록이_없는_투수는_비율값이_null() {
        PitcherCardStats s = service.getCardStats("20250601HHKT02025", List.of(61895)).get(61895);

        assertThat(s.season().whip()).isNull();
        assertThat(s.season().ip()).isZero();
        assertThat(s.vsBatterHand().right().avg()).isNull();
        assertThat(s.fatigue().daysRest()).isNull();
        assertThat(s.qualityPercentile()).isNull();
    }

    @Test
    void 명단에_없는_투수는_결과에서_빠진다() {
        Map<Integer, PitcherCardStats> result = service.getCardStats("20250601HHKT02025", List.of(60841, 99999));
        assertThat(result).containsOnlyKeys(60841);
        assertThat(service.getCardStats("20250601HHKT02025", List.of())).isEmpty();
    }

    @Test
    void JSON_키는_명세와_같다() {
        JsonNode json = objectMapper.valueToTree(service.getCardStats("20250601HHKT02025", List.of(60841)).get(60841));

        assertThat(json.get("vs_batter_hand").has("L")).isTrue();
        assertThat(json.get("vs_batter_hand").has("R")).isTrue();
        assertThat(json.get("season").get("era").isNull()).isTrue();
        assertThat(json.get("fatigue").get("appearances_last_7d").asInt()).isEqualTo(3);
        assertThat(json.get("fatigue").get("pitches_last_3d").asInt()).isEqualTo(35);
        assertThat(json.get("quality_percentile").asInt()).isEqualTo(84);
    }
}
