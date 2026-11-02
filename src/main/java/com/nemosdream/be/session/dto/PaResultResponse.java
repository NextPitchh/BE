package com.nemosdream.be.session.dto;

import com.nemosdream.be.simulation.domain.PaOutcome;
import java.util.Map;

/**
 * 명세 §2-4 PAResult.
 * probability = 나온 결과의 확률, probs = 9종 전체 분포 (합 1). 실제 기록을 재생한 타석도 모델 확률을 같이 보여준다.
 * delta_we_attacker 는 이 타석 공격팀 관점 승률 변화, delta_we_defender 는 그 반대 부호.
 * source: "actual" = 실제 기록 재생 (분기 전, 실제와 같은 투수) / "simulated" = 주사위
 */
public record PaResultResponse(
        PaOutcome outcome,
        String outcomeLabel,
        String description,
        double probability,
        Map<String, Double> probs,
        int runsScored,
        double deltaWeAttacker,
        double deltaWeDefender,
        String source,
        GameStateResponse nextState
) {
}
