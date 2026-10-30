package com.nemosdream.be.simulation.domain;

/**
 * log5 계산 단위 8칸. 결과 9종(PaOutcome)에서 땅볼 아웃과 병살을 GROUND_BALL 하나로 합친 것이다.
 * 병살은 상황(1루 주자, 0~1아웃)에 따라서만 가능해서 확률 계산 마지막에 나눈다.
 */
public enum OutcomeBucket {
    SINGLE, DOUBLE, TRIPLE, HOME_RUN, WALK, STRIKEOUT, FLY_OUT, GROUND_BALL
}
