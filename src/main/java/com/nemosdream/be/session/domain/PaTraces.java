package com.nemosdream.be.session.domain;

/** PaTrace 계산 도움 */
public final class PaTraces {

    private PaTraces() {
    }

    /** 상태 s 에서 시작하는 타석이 그 반이닝 몇 번째인가 = 같은 반이닝에서 이미 진행한 타석 수 */
    public static int paInHalf(SimState s) {
        return (int) s.trace().stream().filter(t -> t.inning() == s.inning() && t.half() == s.half()).count();
    }
}
