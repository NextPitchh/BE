package com.nemosdream.be.session.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 이닝별 득점 (1~9회). 아직 시작하지 않은 반이닝은 null — 화면에 "-" 로 표시한다 (명세 §2-1). */
public record Linescore(List<Integer> away, List<Integer> home) {

    public Linescore {
        away = Collections.unmodifiableList(new ArrayList<>(away));
        home = Collections.unmodifiableList(new ArrayList<>(home));
    }

    public static Linescore empty() {
        return new Linescore(Collections.nCopies(9, null), Collections.nCopies(9, null));
    }

    /** 반이닝 득점에 runs 를 더한다 (시작 전이면 0 에서 시작) */
    public Linescore add(int inning, Half half, int runs) {
        List<Integer> a = new ArrayList<>(away);
        List<Integer> h = new ArrayList<>(home);
        List<Integer> target = half.awayBats() ? a : h;
        Integer current = target.get(inning - 1);
        target.set(inning - 1, (current == null ? 0 : current) + runs);
        return new Linescore(a, h);
    }
}
