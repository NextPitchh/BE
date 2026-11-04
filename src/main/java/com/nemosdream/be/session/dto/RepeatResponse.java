package com.nemosdream.be.session.dto;

import java.util.Map;

/**
 * POST /sessions/{id}/repeat — "같은 선택으로 n회 반복".
 * 세션 시작 지점부터 경기 끝까지, 사용자의 투수 선택 순서는 고정하고 타석 결과만 다시 뽑는다. 세션 상태는 바꾸지 않는다.
 *
 * @param finalWe              경기 종료 시 my_team 승률 분포 (이기면 1, 지면 0, 동점 0.5)
 * @param winRate              추가 필드: 이긴 비율
 * @param betterThanActualRate 실제 경기(같은 시작 지점)보다 내준 점수가 적었던 비율
 * @param actual               추가 필드: 비교 기준인 실제 경기
 */
public record RepeatResponse(int n, RunsAllowed runsAllowed, FinalWe finalWe, double winRate,
                             double betterThanActualRate, Actual actual, long elapsedMs) {

    /** histogram: 내준 점수 → 횟수 (점수 오름차순) */
    public record RunsAllowed(Map<String, Integer> histogram, double median, double mean) {
    }

    public record FinalWe(double median, double p10, double p90) {
    }

    public record Actual(int runsAllowed, String result) {
    }
}
