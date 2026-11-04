package com.nemosdream.be.session.domain;

/**
 * 이 경기에서 투수의 지금까지 기록 (명세 workload, /recommend this_outing).
 * 분기 전은 실제 기록, 분기 후 투구 수는 타석마다 그 투수의 타석당 평균을 더한 추정값이다 (명세 §5).
 *
 * @param outs        잡은 아웃 (타석 사이 주자 아웃 포함)
 * @param hitsAllowed 피안타
 * @param walks       볼넷 (사구 포함)
 */
public record PitcherDay(int paFaced, double pitches, int outs, int hitsAllowed, int walks) {

    public static final PitcherDay NONE = new PitcherDay(0, 0, 0, 0, 0);

    /** 타석 하나를 더한다. pitches 는 새 누적값 */
    public PitcherDay plus(int pa, double newPitches, int newOuts, boolean hit, boolean walk) {
        return new PitcherDay(paFaced + pa, newPitches, outs + newOuts, hitsAllowed + (hit ? 1 : 0),
                walks + (walk ? 1 : 0));
    }
}
