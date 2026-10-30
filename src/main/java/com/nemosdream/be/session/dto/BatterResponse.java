package com.nemosdream.be.session.dto;

import com.nemosdream.be.batter.dto.BatterSeasonStats;
import com.nemosdream.be.matchup.dto.VsCurrentPitcher;
import java.util.List;

/**
 * 명세 §2-3 Batter. vs_current_pitcher 는 현재 투수와 첫 대결이면 null (0 으로 채우지 않는다).
 * position 은 선발 타자만 있다 (교체 선수는 null).
 */
public record BatterResponse(
        int batterId,
        String name,
        String hitType,
        String photoUrl,
        int order,
        String position,
        BatterSeasonStats season,
        Double recentAvg,
        VsCurrentPitcher vsCurrentPitcher,
        Today today
) {

    /** 이 경기 기록. rbi 는 그 타자 타석에서 들어온 점수로 센다 (실책·폭투 득점 구분 없음) */
    public record Today(int ab, int h, int rbi, List<String> log) {
    }
}
