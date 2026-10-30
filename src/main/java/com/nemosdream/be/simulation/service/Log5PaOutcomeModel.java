package com.nemosdream.be.simulation.service;

import com.nemosdream.be.simulation.domain.BucketCounts;
import com.nemosdream.be.simulation.domain.LeagueRates;
import com.nemosdream.be.simulation.domain.Log5;
import com.nemosdream.be.simulation.domain.PaContext;
import com.nemosdream.be.simulation.domain.PaOutcome;
import com.nemosdream.be.simulation.entity.OutcomeCount;
import com.nemosdream.be.simulation.entity.OutcomeCountId;
import com.nemosdream.be.simulation.properties.SimulationProperties;
import com.nemosdream.be.simulation.repository.LeagueOutcomeRateRepository;
import com.nemosdream.be.simulation.repository.OutcomeCountRepository;
import java.util.Map;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * log5 타석 결과 모델. 선수 기록은 outcome_counts(그 경기 전 2025 시즌), 리그 기준은 league_outcome_rates(2024).
 * 기록이 없는 선수(시즌 첫 타석·첫 등판)는 리그 비율 그대로 쓴다.
 */
@Service
@Transactional(readOnly = true)
@EnableConfigurationProperties(SimulationProperties.class)
public class Log5PaOutcomeModel implements PaOutcomeModel {

    private final OutcomeCountRepository counts;
    private final LeagueOutcomeRateRepository leagueRates;
    private final SimulationProperties properties;
    private volatile LeagueRates league;

    public Log5PaOutcomeModel(OutcomeCountRepository counts, LeagueOutcomeRateRepository leagueRates,
                              SimulationProperties properties) {
        this.counts = counts;
        this.leagueRates = leagueRates;
        this.properties = properties;
    }

    @Override
    public Map<PaOutcome, Double> probabilities(PaContext c) {
        BucketCounts batter = find(c.gameId(), c.batterId(), OutcomeCountId.BATTER);
        BucketCounts pitcher = find(c.gameId(), c.pitcherId(), OutcomeCountId.PITCHER);
        return Log5.probabilities(batter, pitcher, league(), properties.log5K(),
                Log5.isDoublePlayPossible(c.outs(), c.bases()));
    }

    @Override
    public String name() {
        return "log5-k" + (int) properties.log5K();
    }

    private BucketCounts find(String gameId, int playerId, String role) {
        return counts.findById(new OutcomeCountId(gameId, playerId, role))
                .map(OutcomeCount::toBucketCounts)
                .orElse(BucketCounts.NONE);
    }

    private LeagueRates league() {
        LeagueRates loaded = league;
        if (loaded == null) {
            loaded = leagueRates.findById(properties.leagueSeason())
                    .orElseThrow(() -> new IllegalStateException(
                            "league_outcome_rates 에 " + properties.leagueSeason() + " 이 없습니다 (etl/load.sh 확인)"))
                    .toLeagueRates();
            league = loaded;
        }
        return loaded;
    }
}
