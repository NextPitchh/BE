package com.nemosdream.be.pitcher.dto;

import com.nemosdream.be.pitcher.entity.PitcherGameStats;

/** 투수 카드에 들어갈 경기 전 기록 묶음. qualityPercentile 은 이전 기록이 없으면 null. */
public record PitcherCardStats(SeasonStats season, VsBatterHand vsBatterHand, Fatigue fatigue, Integer qualityPercentile) {

    public static PitcherCardStats from(PitcherGameStats s) {
        return new PitcherCardStats(SeasonStats.from(s), VsBatterHand.from(s), Fatigue.from(s),
                s.getQualityPercentile() == null ? null : s.getQualityPercentile().intValue());
    }
}
