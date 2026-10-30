package com.nemosdream.be.session.domain;

/** 실제 경기와 처음 달라진 지점. paIndex 는 그 시점의 실제 타석 번호 (game_pa.pa_index). */
public record DivergedAt(int inning, Half half, int paIndex) {
}
