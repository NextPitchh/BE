package com.nemosdream.be.simulation.domain;

/**
 * 주자 진루 규칙 (팀 결정, 결정론적). 프론트는 재구현하지 않는다 (명세 §5).
 *
 * <pre>
 * 1루타   타자 1루, 1루 주자 → 3루, 2·3루 주자 홈인
 * 2루타   타자 2루, 주자 전원 홈인
 * 3루타   타자 3루, 주자 전원 홈인
 * 홈런    타자·주자 전원 홈인
 * 볼넷    밀려나는 주자만 한 칸씩 (만루면 밀어내기 1점)
 * 삼진·땅볼·플라이   아웃 1, 주자 그대로 (희생플라이 없음)
 * 병살    타자 + 1루 주자 아웃. 0아웃이었으면 3루 주자 홈인, 2루 주자는 그대로
 *         2아웃이거나 1루가 비어 있으면 병살이 불가능해 땅볼 아웃으로 바꾼다
 * 3아웃이 되면 그 타석에서는 득점하지 않는다
 * </pre>
 */
public final class RunnerAdvancement {

    private RunnerAdvancement() {
    }

    public static AdvanceResult apply(int outs, Bases bases, PaOutcome outcome) {
        if (outs < 0 || outs > 2) {
            throw new IllegalArgumentException("타석 전 아웃은 0~2 여야 합니다: " + outs);
        }
        return switch (outcome) {
            case SINGLE -> new AdvanceResult(outcome, outs, new Bases(true, false, bases.first()),
                    count(bases.second()) + count(bases.third()));
            case DOUBLE -> new AdvanceResult(outcome, outs, new Bases(false, true, false), bases.runnerCount());
            case TRIPLE -> new AdvanceResult(outcome, outs, new Bases(false, false, true), bases.runnerCount());
            case HOME_RUN -> new AdvanceResult(outcome, outs, Bases.EMPTY, bases.runnerCount() + 1);
            case WALK -> walk(outs, bases);
            case STRIKEOUT, GROUND_OUT, FLY_OUT -> out(outcome, outs, bases);
            case DOUBLE_PLAY -> doublePlay(outs, bases);
        };
    }

    private static AdvanceResult walk(int outs, Bases b) {
        boolean loaded = b.first() && b.second() && b.third();
        Bases next = new Bases(true, b.second() || b.first(), b.third() || (b.first() && b.second()));
        return new AdvanceResult(PaOutcome.WALK, outs, next, loaded ? 1 : 0);
    }

    private static AdvanceResult out(PaOutcome outcome, int outs, Bases b) {
        int after = outs + 1;
        return new AdvanceResult(outcome, after, after == 3 ? Bases.EMPTY : b, 0);
    }

    private static AdvanceResult doublePlay(int outs, Bases b) {
        if (outs == 2 || !b.first()) {
            return out(PaOutcome.GROUND_OUT, outs, b);
        }
        if (outs == 1) {
            return new AdvanceResult(PaOutcome.DOUBLE_PLAY, 3, Bases.EMPTY, 0);
        }
        // 0아웃 → 2아웃. 3루 주자 홈인, 2루 주자 그대로
        return new AdvanceResult(PaOutcome.DOUBLE_PLAY, 2, new Bases(false, b.second(), false), count(b.third()));
    }

    private static int count(boolean runner) {
        return runner ? 1 : 0;
    }
}
