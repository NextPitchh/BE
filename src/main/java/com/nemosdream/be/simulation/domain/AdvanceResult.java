package com.nemosdream.be.simulation.domain;

/**
 * 한 타석 뒤의 아웃·주자·득점.
 * outcome 은 실제로 적용된 결과다. 병살이 불가능한 상황에서 병살(GDP)이 뽑히면 땅볼 아웃(GO)으로 바뀐다.
 * outs 가 3 이면 이닝이 끝난 것이고, 이때 bases 는 비어 있다.
 */
public record AdvanceResult(PaOutcome outcome, int outs, Bases bases, int runsScored) {

    public boolean inningEnded() {
        return outs >= 3;
    }
}
