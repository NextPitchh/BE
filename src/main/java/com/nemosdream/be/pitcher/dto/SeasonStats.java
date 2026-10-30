package com.nemosdream.be.pitcher.dto;

import com.nemosdream.be.global.util.Decimals;
import com.nemosdream.be.pitcher.entity.PitcherGameStats;

/**
 * Pitcher.season. ip 는 야구 표기 (52.1 = 52와 1/3 이닝).
 * era 는 항상 null — 원본에 자책점 구분이 없어 계산할 수 없다.
 */
public record SeasonStats(Double era, Double whip, double ip, int so, int bb, int g) {

    public static SeasonStats from(PitcherGameStats s) {
        return new SeasonStats(null, Decimals.round3(s.getWhip()), inningsNotation(s.getOuts()), s.getSo(), s.getBb(), s.getG());
    }

    /** 아웃 수 → 야구 이닝 표기. 157 아웃 → 52.1 */
    static double inningsNotation(int outs) {
        return outs / 3 + (outs % 3) / 10.0;
    }
}
