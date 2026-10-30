package com.nemosdream.be.session.domain;

/**
 * 사용자의 투수 선택 한 번 (명세 §3-10 결정 비교용).
 *
 * @param actualPitcherId 분기 전이면 그 타석 실제 투수, 분기 후면 null (비교할 실제 기록 없음)
 * @param changed         현재 투수를 바꿨나
 */
public record Decision(int inning, Half half, int outs, String baseState, int chosenPitcherId,
                       Integer actualPitcherId, boolean changed) {

    public boolean agreesWithActual() {
        return actualPitcherId != null && actualPitcherId == chosenPitcherId;
    }
}
