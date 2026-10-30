package com.nemosdream.be.batter.dto;

import com.nemosdream.be.batter.entity.BatterGameStats;
import com.nemosdream.be.global.util.Decimals;

/** 명세 §2-3 Batter.season. 타수가 0 이면 비율 값은 null. */
public record BatterSeasonStats(Double avg, Double obp, Double slg, Double ops, int hr) {

    public static BatterSeasonStats from(BatterGameStats s) {
        return new BatterSeasonStats(Decimals.round3(s.getAvg()), Decimals.round3(s.getObp()),
                Decimals.round3(s.getSlg()), Decimals.round3(s.getOps()), s.getHr());
    }
}
