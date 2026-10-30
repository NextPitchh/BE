package com.nemosdream.be.simulation.domain;

/** 한 선수의 그 경기 전 타석 결과 수 (outcome_counts 한 행). pa 는 8칸의 합. */
public record BucketCounts(int pa, int single, int dbl, int triple, int homeRun, int walk,
                           int strikeout, int flyOut, int groundBall) {

    public static final BucketCounts NONE = new BucketCounts(0, 0, 0, 0, 0, 0, 0, 0, 0);

    public int count(OutcomeBucket bucket) {
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
