package com.nemosdream.be.session.domain;

/** 이 경기에서 투수가 지금까지 상대한 타자 수와 투구 수. 분기 후 투구 수는 타석당 평균으로 늘린다 (명세 §5). */
public record PitcherDay(int paFaced, int pitches) {
}
