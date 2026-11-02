package com.nemosdream.be.simulation.ai;

import com.nemosdream.be.session.domain.SimState;
import com.nemosdream.be.simulation.domain.PaOutcome;
import java.util.Map;

/** 팀원 엔진이 현재 상태와 검증된 완료 구수를 제공하는 확률 공급 경계. */
public interface PaProbabilityProvider {
    Map<PaOutcome, Double> probabilities(String gameId, SimState state, CompletedPitchCounts pitches);
    String modelName();
}
