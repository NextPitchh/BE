package com.nemosdream.be.session.dto;

import com.nemosdream.be.session.domain.Half;
import java.util.List;

/**
 * 명세 §3-9 GET /sessions/{id}/we-trace (+ §3-10 레버리지 추이를 합쳐서 반환).
 * we 는 perspective(my_team) 관점, 각 반이닝이 끝난 시점 값. 진행 중인 반이닝은 지금 값.
 * leverage_index 는 그 반이닝 타석 중 가장 높은 LI (승부처 표시용).
 *
 * @param actual    실제 경기 전체 (1회 ~ 실제 종료)
 * @param simulated 분기한 반이닝부터 지금까지. 분기 전이면 빈 배열
 * @param divergeAt 분기 전이면 null
 */
public record WeTraceResponse(String perspective, List<HalfPoint> actual, List<HalfPoint> simulated,
                              DivergeAt divergeAt, double current) {

    public record HalfPoint(int inning, Half half, double we, double leverageIndex) {
    }

    public record DivergeAt(int inning, Half half) {
    }
}
