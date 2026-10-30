package com.nemosdream.be.matchup.dto;

import com.nemosdream.be.global.util.Decimals;
import com.nemosdream.be.matchup.entity.Matchup;

/**
 * 명세 §2-3 Batter.vs_current_pitcher. ab·h·avg 는 실제 상대 기록, woba·iso 는 보정값.
 * 첫 대결이면 이 객체 대신 null 을 보낸다 (명세: 0 으로 채우지 않는다).
 */
public record VsCurrentPitcher(int ab, int h, Double avg, double woba, double iso) {

    public static VsCurrentPitcher from(Matchup m) {
        return new VsCurrentPitcher(m.getAb(), m.getH(), Decimals.round3(m.getAvg()),
                Decimals.round3(m.getWoba()), Decimals.round3(m.getIso()));
    }
}
