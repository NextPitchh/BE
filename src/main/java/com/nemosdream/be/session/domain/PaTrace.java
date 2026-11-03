package com.nemosdream.be.session.domain;

/**
 * 진행한 타석 하나 (양 팀 모두, 세션 시작 이후). 레버리지 추이와 결과 화면(투수 운용, 결정 비교)에 쓴다.
 *
 * @param paInHalf     그 반이닝의 몇 번째 타석인가 (0부터)
 * @param leverageIndex 타석 시작 LI
 * @param awayWeBefore  타석 전 원정팀 관점 승률
 * @param awayWeAfter   타석 후 원정팀 관점 승률
 */
public record PaTrace(int inning, Half half, int paInHalf, int pitcherId, String outcome, double leverageIndex,
                      double awayWeBefore, double awayWeAfter) {

    /** 원정팀이 수비한 타석인가 (말 = 원정 수비) */
    public boolean awayFielding() {
        return !half.awayBats();
    }
}
