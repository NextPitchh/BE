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

    /**
     * @param runsScored 이 타석으로 내 팀이 낸 점수. 직전 타석 이후 타석 아닌 기록(도루·폭투 등)으로 난 점수도 포함
     *                   (그래서 pa_log 의 runs_scored 합 = summary.runs)
     * @param deltaWe    my_team 관점 승률 변화 = we_trace[i+1] - we_trace[i]
     */
    public record PaLogEntry(int batterId, String batter, PaOutcome outcome, String description, int runsScored,
                             double deltaWe) {
    }
}
