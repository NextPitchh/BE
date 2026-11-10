package com.nemosdream.be.session.dto;

import com.nemosdream.be.session.domain.Half;
import java.util.List;

/**
 * 명세 GET /sessions/{id}/result — 결과 화면. 비교 범위는 세션 시작 지점부터 끝(또는 지금)까지.
 * 모델 순위(model_top, model_rank)는 그 결정 직전 교체 후보 안의 밴딧 순위. 모델이 없거나 응답하지 않으면 null.
 */
public record ResultResponse(
        Side simulated,
        Side actual,
        List<PitcherUsage> pitcherUsage,
        List<PitcherUsage> actualPitcherUsage,
        List<DecisionRow> decisions,
        Agreement agreement,
        BullpenUsed bullpenUsed
) {

    /**
     * @param runsAllowedInRange 세션 시작부터 내 팀이 내준 점수
     * @param finalWe            my_team 관점. 경기가 끝났으면 1 / 0 / 0.5
     * @param result             "win" | "loss" | "tie" | "in_progress"
     */
    public record Side(Score score, int runsAllowedInRange, double finalWe, String result) {
    }

    public record Score(int my, int opp) {
    }

    /** 내 팀 투수 한 명의 등판 구간. pa = 그 반이닝의 몇 번째 타석 (0부터), pitches_est = 이 구간 투구 수 */
    public record PitcherUsage(int pitcherId, String name, PaPoint from, PaPoint to, double deltaWeDefender,
                               int pitchesEst, double liAtEntry) {
    }

    public record PaPoint(int inning, Half half, int pa) {
    }

    /**
     * 결정 비교 테이블 한 줄 = 내 팀 수비 타석 하나. is_change = 그 타석에서 투수를 바꿨나.
     * actual_choice 는 분기 후 null (비교할 실제 기록이 없다).
     */
    public record DecisionRow(int inning, Half half, double leverageIndex, PitcherRef modelTop, Choice userChoice,
                              Choice actualChoice, String outcome, double deltaWeDefender, boolean isChange) {
    }

    public record PitcherRef(int pitcherId, String name) {
    }

    public record Choice(int pitcherId, String name, Integer modelRank) {
    }

    /**
     * 결정 비교 요약.
     *
     * @param mineInTop3    내가 교체한 결정 중 고른 투수가 모델 상위 3위 안이었던 횟수 (분모 my_changes)
     * @param actualInTop3  실제 감독이 교체한 결정(분기 전) 중 상위 3위 안이었던 횟수 (분모 actual_changes)
     * @param sameAsActual  분기 전 결정 중 실제 감독과 같은 투수를 고른 횟수
     * @param myChanges     추가 필드: 내가 투수를 바꾼 횟수
     * @param actualChanges 추가 필드: 분기 전 결정 중 실제 감독이 투수를 바꾼 횟수
     */
    public record Agreement(int totalDecisions, Integer mineInTop3, Integer actualInTop3, int sameAsActual,
                            int myChanges, int actualChanges) {
    }

    public record BullpenUsed(int mine, int actual, int total) {
    }
}
