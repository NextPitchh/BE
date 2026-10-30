package com.nemosdream.be.batter.dto;

import com.nemosdream.be.batter.entity.BatterGameStats;
import com.nemosdream.be.global.util.Decimals;

/**
 * 타자 카드에 들어갈 경기 전 기록 묶음. recentAvg 는 직전 10경기 타율 (타수 0 이면 null).
 * vs_current_pitcher, today 는 각각 matchups 와 세션이 채운다.
 */
public record BatterCardStats(BatterSeasonStats season, Double recentAvg) {

    public static BatterCardStats from(BatterGameStats s) {
        return new BatterCardStats(BatterSeasonStats.from(s), Decimals.round3(s.getRecentAvg()));
    }
}
