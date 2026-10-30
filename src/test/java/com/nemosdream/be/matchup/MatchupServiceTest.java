package com.nemosdream.be.matchup;

import static org.assertj.core.api.Assertions.assertThat;

import com.nemosdream.be.matchup.dto.MatchupLabel;
import com.nemosdream.be.matchup.dto.VsCurrentPitcher;
import com.nemosdream.be.matchup.service.MatchupService;
import com.nemosdream.be.support.PostgresTestSupport;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@Sql(scripts = {
        "file:etl/schema/01_teams_players.sql",
        "file:etl/schema/02_games_pa.sql",
        "file:etl/schema/08_matchups.sql",
        "classpath:sql/fixture-matchups.sql"
})
@Sql(statements = "DROP TABLE IF EXISTS matchups, game_pa, games, players, teams CASCADE",
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class MatchupServiceTest extends PostgresTestSupport {

    private static final String GAME = "20250601HHKT02025";

    @Autowired
    MatchupService service;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void 타자_카드_상대_전적_첫_대결은_null() {
        Map<Integer, Optional<VsCurrentPitcher>> result = service.vsPitcher(GAME, 60841, List.of(51234, 51235, 99999));

        assertThat(result).containsOnlyKeys(51234, 51235);
        assertThat(result.get(51234)).contains(new VsCurrentPitcher(10, 4, 0.4, 0.345, 0.15));
        assertThat(result.get(51235)).isEmpty();
    }

    @Test
    void 교체_후보의_유리_불리_판정() {
        assertThat(service.labelsVsBatter(GAME, List.of(60841, 60842, 99999), 51234)).containsOnly(
                Map.entry(60841, Optional.of(MatchupLabel.UNFAVORABLE)),
                Map.entry(60842, Optional.of(MatchupLabel.FAVORABLE)));
    }

    @Test
    void 첫_대결은_좌우_기준값으로_판정하고_좌우를_모르면_판정하지_않는다() {
        assertThat(service.labelsVsBatter(GAME, List.of(60841, 60843), 51235)).containsOnly(
                Map.entry(60841, Optional.of(MatchupLabel.NEUTRAL)),
                Map.entry(60843, Optional.empty()));
    }

    @Test
    void 판정은_소문자로_나간다() {
        assertThat(objectMapper.writeValueAsString(MatchupLabel.UNFAVORABLE)).isEqualTo("\"unfavorable\"");
        assertThat(service.labelsVsBatter(GAME, List.of(), 51234)).isEmpty();
    }
}
