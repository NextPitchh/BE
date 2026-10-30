package com.nemosdream.be.batter;

import static org.assertj.core.api.Assertions.assertThat;

import com.nemosdream.be.batter.dto.BatterCardStats;
import com.nemosdream.be.batter.dto.BatterSeasonStats;
import com.nemosdream.be.batter.service.BatterStatsService;
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
        "file:etl/schema/07_batter_game_stats.sql",
        "classpath:sql/fixture-batter-stats.sql"
})
@Sql(statements = "DROP TABLE IF EXISTS batter_game_stats, game_pa, games, players, teams CASCADE",
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class BatterStatsServiceTest extends PostgresTestSupport {

    @Autowired
    BatterStatsService service;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void 경기_전_카드_기록을_명세_형태로_만든다() {
        BatterCardStats s = service.getCardStats("20250601HHKT02025", List.of(51234)).get(51234);

        assertThat(s.season()).isEqualTo(new BatterSeasonStats(0.286, 0.361, 0.492, 0.853, 22));
        assertThat(s.recentAvg()).isEqualTo(0.312);
    }

    @Test
    void 기록이_없는_타자는_비율값이_null() {
        BatterCardStats s = service.getCardStats("20250601HHKT02025", List.of(51235)).get(51235);

        assertThat(s.season()).isEqualTo(new BatterSeasonStats(null, null, null, null, 0));
        assertThat(s.recentAvg()).isNull();
    }

    @Test
    void 그_경기에_없는_타자는_결과에서_빠진다() {
        Map<Integer, BatterCardStats> result = service.getCardStats("20250601HHKT02025", List.of(51234, 99999));
        assertThat(result).containsOnlyKeys(51234);
        assertThat(service.getCardStats("20250601HHKT02025", List.of())).isEmpty();
    }

    @Test
    void JSON_키는_명세와_같다() {
        JsonNode json = objectMapper.valueToTree(service.getCardStats("20250601HHKT02025", List.of(51234)).get(51234));

        assertThat(json.get("season").get("ops").asDouble()).isEqualTo(0.853);
        assertThat(json.get("recent_avg").asDouble()).isEqualTo(0.312);
    }
}
