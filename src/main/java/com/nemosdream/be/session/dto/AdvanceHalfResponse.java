package com.nemosdream.be.session.dto;

import com.nemosdream.be.simulation.domain.PaOutcome;
import java.util.List;

/**
 * 명세 §3-7 POST /sessions/{id}/advance-half 응답.
 * state = 진행 후 상태 (보통 내 팀 수비 반이닝 시작, 경기가 끝났으면 종료 상태).
 */
public record AdvanceHalfResponse(Summary summary, GameStateResponse state) {

    /**
     * @param runs    이 반이닝 내 팀 득점
     * @param hits    이 반이닝 내 팀 안타
     * @param weTrace my_team 관점 승률. 반이닝 시작 값 + 타석마다 하나 (길이 = pa_log + 1)
     * @param source  "actual" = 실제 기록 재생 (분기 전) / "simulated" = 주사위 (분기 후)
     */
    public record Summary(int runs, int hits, List<PaLogEntry> paLog, List<Double> weTrace, String source) {
    }

    public record PaLogEntry(int batterId, String batter, PaOutcome outcome, String description) {
    }
}
