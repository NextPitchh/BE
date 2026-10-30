package com.nemosdream.be.session.domain;

import java.util.List;

/** 이 경기에서 타자의 기록 (Batter.today). log 는 결과 코드 순서대로. rbi 는 그 타석 득점 수로 센다. */
public record BatterDay(int ab, int h, int rbi, List<String> log) {

    public static final BatterDay NONE = new BatterDay(0, 0, 0, List.of());

    public BatterDay {
        log = List.copyOf(log);
    }
}
