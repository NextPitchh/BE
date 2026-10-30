package com.nemosdream.be.simulation.domain;

/** 타석 결과 확률을 구할 때 필요한 정보. 선수 기록은 game_id 기준 경기 전 값을 쓴다. */
public record PaContext(String gameId, int pitcherId, int batterId, int outs, Bases bases) {
}
