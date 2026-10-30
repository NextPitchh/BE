package com.nemosdream.be.session.domain;

import com.nemosdream.be.game.entity.GamePa;

/**
 * 실제 타석 한 행 (game_pa). 세션이 경기 기록을 메모리에 들고 있으면서 분기 전 재생에 쓴다.
 * 상황 값(아웃·주자·점수)은 타석 시작 시점이다.
 */
public record ActualPa(
        int paIndex,
        int inning,
        Half half,
        int outs,
        String baseState,
        int scoreHome,
        int scoreAway,
        int batterId,
        int pitcherId,
        int battingOrder,
        String result,
        String relayText,
        int runsScored,
        int pitchCountBefore,
        int pitchesInPa,
        boolean partial,
        boolean inningEnded
) {

    public static ActualPa of(GamePa p) {
        return new ActualPa(p.getPaIndex(), p.getInning(), Half.of(p.getHalf()), p.getOutCount(), p.getBaseState(),
                p.getScoreHome(), p.getScoreAway(), p.getBatterId(), p.getPitcherId(), p.getBattingOrder(),
                p.getResult(), p.getRelayText(), p.getRunsScored(), p.getPitchCountBefore(), p.getPitchesInPa(),
                p.isPartialPa(), p.isInningEnded());
    }

    public boolean sameHalfAs(ActualPa other) {
        return inning == other.inning && half == other.half;
    }

    /** 이 타석 공격팀 점수 */
    public int attackingScore() {
        return half.awayBats() ? scoreAway : scoreHome;
    }
}
