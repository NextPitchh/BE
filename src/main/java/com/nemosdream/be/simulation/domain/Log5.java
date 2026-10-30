package com.nemosdream.be.simulation.domain;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * log5 (다항 odds-ratio): P(결과 i) ∝ 타자 비율_i × 투수 비율_i / 리그 비율_i, 합이 1 이 되게 나눈다.
 * 선수 비율은 표본이 작을수록 리그 쪽으로 당긴다: (횟수_i + k × 리그_i) / (타석 + k).
 * 2025 실제 타석 기준 리그 비율만 쓸 때보다 log loss 1.804 → 1.782 (k = 200), etl/10_outcome_counts.py 참고.
 */
public final class Log5 {

    private Log5() {
    }

    /**
     * @param dpOpportunity 1루 주자가 있고 2아웃 전이면 true. 이때만 땅볼을 리그 병살 비율로 땅볼 아웃 / 병살로 나눈다
     * @return 결과 9종 → 확률 (합 1). 병살이 불가능하면 병살 확률은 0
     */
    public static Map<PaOutcome, Double> probabilities(BucketCounts batter, BucketCounts pitcher, LeagueRates league,
                                                       double k, boolean dpOpportunity) {
        EnumMap<OutcomeBucket, Double> raw = new EnumMap<>(OutcomeBucket.class);
        double sum = 0;
        for (OutcomeBucket b : OutcomeBucket.values()) {
            double leagueRate = league.rate(b);
            double x = shrunk(batter, b, leagueRate, k) * shrunk(pitcher, b, leagueRate, k) / leagueRate;
            raw.put(b, x);
            sum += x;
        }

        EnumMap<PaOutcome, Double> p = new EnumMap<>(PaOutcome.class);
        p.put(PaOutcome.SINGLE, raw.get(OutcomeBucket.SINGLE) / sum);
        p.put(PaOutcome.DOUBLE, raw.get(OutcomeBucket.DOUBLE) / sum);
        p.put(PaOutcome.TRIPLE, raw.get(OutcomeBucket.TRIPLE) / sum);
        p.put(PaOutcome.HOME_RUN, raw.get(OutcomeBucket.HOME_RUN) / sum);
        p.put(PaOutcome.WALK, raw.get(OutcomeBucket.WALK) / sum);
        p.put(PaOutcome.STRIKEOUT, raw.get(OutcomeBucket.STRIKEOUT) / sum);
        p.put(PaOutcome.FLY_OUT, raw.get(OutcomeBucket.FLY_OUT) / sum);
        double groundBall = raw.get(OutcomeBucket.GROUND_BALL) / sum;
        double dpShare = dpOpportunity ? league.dpShare() : 0.0;
        p.put(PaOutcome.GROUND_OUT, groundBall * (1 - dpShare));
        p.put(PaOutcome.DOUBLE_PLAY, groundBall * dpShare);
        return Collections.unmodifiableMap(p);
    }

    public static boolean isDoublePlayPossible(int outs, Bases bases) {
        return bases.first() && outs < 2;
    }

    private static double shrunk(BucketCounts c, OutcomeBucket b, double leagueRate, double k) {
        return (c.count(b) + k * leagueRate) / (c.pa() + k);
    }
}
