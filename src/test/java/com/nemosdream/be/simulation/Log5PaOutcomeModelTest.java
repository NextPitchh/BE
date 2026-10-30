package com.nemosdream.be.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.nemosdream.be.simulation.domain.Bases;
import com.nemosdream.be.simulation.domain.BucketCounts;
import com.nemosdream.be.simulation.domain.LeagueRates;
import com.nemosdream.be.simulation.domain.Log5;
import com.nemosdream.be.simulation.domain.PaContext;
import com.nemosdream.be.simulation.domain.PaOutcome;
import com.nemosdream.be.simulation.service.Log5PaOutcomeModel;
import com.nemosdream.be.support.PostgresTestSupport;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest
@Sql(scripts = {
        "file:etl/schema/01_teams_players.sql",
        "file:etl/schema/02_games_pa.sql",
        "file:etl/schema/09_outcome_counts.sql",
        "classpath:sql/fixture-outcome-counts.sql"
})
@Sql(statements = "DROP TABLE IF EXISTS outcome_counts, league_outcome_rates, game_pa, games, players, teams CASCADE",
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class Log5PaOutcomeModelTest extends PostgresTestSupport {

    static final String GAME = "20250601HHKT02025";

    @Autowired
    Log5PaOutcomeModel model;

    @Test
    void DB_기록으로_log5_확률을_계산한다() {
        Map<PaOutcome, Double> p = model.probabilities(new PaContext(GAME, 60841, 51234, 0, Bases.of("1")));

        Map<PaOutcome, Double> expected = Log5.probabilities(
                new BucketCounts(400, 60, 16, 2, 10, 40, 120, 80, 72),
                new BucketCounts(300, 45, 12, 1, 6, 30, 90, 60, 56),
                new LeagueRates(0.183f, 0.042f, 0.004f, 0.025f, 0.104f, 0.189f, 0.237f, 0.216f, 0.338f),
                200, true);
        for (PaOutcome o : PaOutcome.values()) {
            assertThat(p.get(o)).as(o.code()).isCloseTo(expected.get(o), within(1e-9));
        }
        assertThat(p.get(PaOutcome.DOUBLE_PLAY)).isPositive();
        assertThat(model.name()).isEqualTo("log5-k200");
    }

    @Test
    void 기록이_없는_선수는_리그_비율로_계산한다() {
        Map<PaOutcome, Double> p = model.probabilities(new PaContext(GAME, 99999, 88888, 2, Bases.of("1")));

        assertThat(p.get(PaOutcome.STRIKEOUT)).isCloseTo(0.189, within(1e-6));
        assertThat(p.get(PaOutcome.DOUBLE_PLAY)).isZero();   // 2아웃이라 병살 불가능
        assertThat(p.values().stream().mapToDouble(Double::doubleValue).sum()).isCloseTo(1.0, within(1e-9));
    }
}
