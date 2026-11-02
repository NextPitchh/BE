package com.nemosdream.be.session.domain;

/**
 * 이 경기에서 투수가 지금까지 상대한 타자 수와 투구 수.
 * 분기 전은 실제 투구 수, 분기 후는 타석마다 그 투수의 타석당 평균 투구 수를 더한 추정값이다 (명세 §5).
 */
public record PitcherDay(int paFaced, double pitches) {
}
