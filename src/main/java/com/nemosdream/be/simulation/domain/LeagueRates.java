package com.nemosdream.be.simulation.domain;

/**
 * 리그 기준 비율 (league_outcome_rates, 2024 시즌). 8칸의 합은 1.
 * dpShare = 병살 가능 상황의 땅볼 아웃 중 병살 비율.
 */
public record LeagueRates(double single, double dbl, double triple, double homeRun, double walk,
                          double strikeout, double flyOut, double groundBall, double dpShare) {

    public double rate(OutcomeBucket bucket) {
        return switch (bucket) {
            case SINGLE -> single;
            case DOUBLE -> dbl;
            case TRIPLE -> triple;
            case HOME_RUN -> homeRun;
            case WALK -> walk;
            case STRIKEOUT -> strikeout;
            case FLY_OUT -> flyOut;
            case GROUND_BALL -> groundBall;
        };
    }
}
