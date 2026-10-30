package com.nemosdream.be.simulation.domain;

import java.util.Map;
import java.util.random.RandomGenerator;

/**
 * 확률대로 결과 하나를 뽑는다. 같은 seed 의 RandomGenerator 를 쓰면 같은 결과가 나온다 (명세 §5: 재현성을 위해 서버가 seed 관리).
 * 결과는 PaOutcome 선언 순서로 누적해서 고른다.
 */
public final class Dice {

    private Dice() {
    }

    public static PaOutcome roll(Map<PaOutcome, Double> probabilities, RandomGenerator random) {
        double u = random.nextDouble();
        double cumulative = 0;
        PaOutcome last = null;
        for (PaOutcome outcome : PaOutcome.values()) {
            double p = probabilities.getOrDefault(outcome, 0.0);
            if (p <= 0) {
                continue;
            }
            cumulative += p;
            last = outcome;
            if (u < cumulative) {
                return outcome;
            }
        }
        if (last == null) {
            throw new IllegalArgumentException("확률이 모두 0 입니다");
        }
        return last;   // 부동소수 오차로 누적이 1 에 조금 못 미칠 때
    }
}
