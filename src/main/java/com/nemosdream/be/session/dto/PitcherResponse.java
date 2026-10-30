package com.nemosdream.be.session.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nemosdream.be.pitcher.dto.SeasonStats;
import com.nemosdream.be.pitcher.dto.VsBatterHand;

/**
 * 명세 §2-2 Pitcher (현재 투수 / 공통 부분). 교체 후보 전용 필드(model_score 등)는 /recommend 에서 붙인다.
 * 경기 전 기록이 없는 투수는 season, vs_batter_hand 가 null.
 */
public record PitcherResponse(
        int pitcherId,
        String name,
        @JsonProperty("throws") String throwingHand,
        String photoUrl,
        SeasonStats season,
        VsBatterHand vsBatterHand,
        Workload workload,
        String availability,
        String unavailableReason
) {

    /**
     * @param estPitchCount 이 경기 투구 수. 분기 전은 실제 값, 분기 후는 타석당 평균으로 늘린 추정값
     */
    public record Workload(
            int paFacedToday,
            int estPitchCount,
            Double avgPitchesPerPa,
            Integer daysRest,
            @JsonProperty("appearances_last_7d") int appearancesLast7d,
            int consecutiveDays,
            @JsonProperty("pitches_last_3d") int pitchesLast3d
    ) {
    }
}
