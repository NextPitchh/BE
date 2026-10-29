package com.nemosdream.be.pitcher.dto;

/**Pitcher.vs_batter_hand 의 L / R 한 쪽. 표본이 없으면 비율 값은 null. */
public record HandSplit(Double avg, Double woba, Double iso, int pa) {
}
