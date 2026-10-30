package com.nemosdream.be.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.nemosdream.be.simulation.domain.Bases;
import com.nemosdream.be.simulation.domain.BucketCounts;
import com.nemosdream.be.simulation.domain.Dice;
import com.nemosdream.be.simulation.domain.LeagueRates;
import com.nemosdream.be.simulation.domain.Log5;
import com.nemosdream.be.simulation.domain.PaOutcome;
import java.util.EnumMap;
import java.util.Map;
import java.util.Random;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

class Log5Test {

    // 2024 리그 비율 (etl/10_outcome_counts.py 출력, 반올림)
    static final LeagueRates LEAGUE = new LeagueRates(0.183, 0.042, 0.004, 0.025, 0.104, 0.189, 0.237, 0.216, 0.338);
    static final double K = 200;

    @Test
    void 기록이_없으면_리그_비율_그대로() {
        Map<PaOutcome, Double> p = Log5.probabilities(BucketCounts.NONE, BucketCounts.NONE, LEAGUE, K, false);

        assertThat(sum(p)).isCloseTo(1.0, within(1e-12));
        assertThat(p.get(PaOutcome.STRIKEOUT)).isCloseTo(0.189, within(1e-9));
        assertThat(p.get(PaOutcome.GROUND_OUT)).isCloseTo(0.216, within(1e-9));
        assertThat(p.get(PaOutcome.DOUBLE_PLAY)).isZero();
    }

    @Test
    void 병살_가능_상황에서만_땅볼을_병살로_나눈다() {
        Map<PaOutcome, Double> p = Log5.probabilities(BucketCounts.NONE, BucketCounts.NONE, LEAGUE, K, true);

        assertThat(p.get(PaOutcome.DOUBLE_PLAY)).isCloseTo(0.216 * 0.338, within(1e-9));
        assertThat(p.get(PaOutcome.GROUND_OUT) + p.get(PaOutcome.DOUBLE_PLAY)).isCloseTo(0.216, within(1e-9));
        assertThat(sum(p)).isCloseTo(1.0, within(1e-12));

        assertThat(Log5.isDoublePlayPossible(0, Bases.of("1"))).isTrue();
        assertThat(Log5.isDoublePlayPossible(1, Bases.of("13"))).isTrue();
        assertThat(Log5.isDoublePlayPossible(2, Bases.of("1"))).isFalse();
        assertThat(Log5.isDoublePlayPossible(0, Bases.of("23"))).isFalse();
    }

    @Test
    void 삼진이_많은_타자와_투수가_만나면_삼진_확률이_올라간다() {
        // 400타석, 삼진 30% (리그 18.9%)
        BucketCounts strikeoutHeavy = new BucketCounts(400, 60, 16, 2, 10, 40, 120, 80, 72);
        Map<PaOutcome, Double> one = Log5.probabilities(strikeoutHeavy, BucketCounts.NONE, LEAGUE, K, false);
        Map<PaOutcome, Double> both = Log5.probabilities(strikeoutHeavy, strikeoutHeavy, LEAGUE, K, false);

        assertThat(one.get(PaOutcome.STRIKEOUT)).isGreaterThan(0.189);
        assertThat(both.get(PaOutcome.STRIKEOUT)).isGreaterThan(one.get(PaOutcome.STRIKEOUT));
        assertThat(sum(both)).isCloseTo(1.0, within(1e-12));
    }

    @Test
    void 주사위는_누적_확률_구간으로_고른다() {
        Map<PaOutcome, Double> p = new EnumMap<>(PaOutcome.class);
        p.put(PaOutcome.SINGLE, 0.2);
        p.put(PaOutcome.STRIKEOUT, 0.5);
        p.put(PaOutcome.GROUND_OUT, 0.3);

        assertThat(Dice.roll(p, fixed(0.0))).isEqualTo(PaOutcome.SINGLE);
        assertThat(Dice.roll(p, fixed(0.19))).isEqualTo(PaOutcome.SINGLE);
        assertThat(Dice.roll(p, fixed(0.2))).isEqualTo(PaOutcome.STRIKEOUT);
        assertThat(Dice.roll(p, fixed(0.75))).isEqualTo(PaOutcome.GROUND_OUT);
        assertThat(Dice.roll(p, fixed(0.9999999))).isEqualTo(PaOutcome.GROUND_OUT);
    }

    @Test
    void 같은_seed_면_같은_결과() {
        Map<PaOutcome, Double> p = Log5.probabilities(BucketCounts.NONE, BucketCounts.NONE, LEAGUE, K, true);
        Random a = new Random(42);
        Random b = new Random(42);
        for (int i = 0; i < 100; i++) {
            assertThat(Dice.roll(p, a)).isEqualTo(Dice.roll(p, b));
        }
    }

    @Test
    void 많이_굴리면_확률대로_나온다() {
        Map<PaOutcome, Double> p = Log5.probabilities(BucketCounts.NONE, BucketCounts.NONE, LEAGUE, K, true);
        Random random = new Random(7);
        int n = 200_000;
        EnumMap<PaOutcome, Integer> seen = new EnumMap<>(PaOutcome.class);
        for (int i = 0; i < n; i++) {
            seen.merge(Dice.roll(p, random), 1, Integer::sum);
        }
        for (PaOutcome o : PaOutcome.values()) {
            assertThat(seen.getOrDefault(o, 0) / (double) n).as(o.code()).isCloseTo(p.get(o), within(0.005));
        }
    }

    private static double sum(Map<PaOutcome, Double> p) {
        return p.values().stream().mapToDouble(Double::doubleValue).sum();
    }

    private static RandomGenerator fixed(double value) {
        return new RandomGenerator() {
            @Override
            public long nextLong() {
                throw new UnsupportedOperationException();
            }

            @Override
            public double nextDouble() {
                return value;
            }
        };
    }
}
