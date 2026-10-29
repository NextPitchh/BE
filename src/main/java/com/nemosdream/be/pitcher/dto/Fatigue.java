package com.nemosdream.be.pitcher.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nemosdream.be.pitcher.entity.PitcherGameStats;

/**
 * 경기 시작 시점의 피로도.
 * (pa_faced_today, est_pitch_count 는 세션이 경기 진행에 따라 채운다).
 * daysRest 는 시즌 첫 등판 전이면 null.
 * 숫자로 끝나는 이름은 snake_case 변환이 "last7d" 로 붙여 버려서 명세 이름을 직접 적는다.
 */
public record Fatigue(Double avgPitchesPerPa, Integer daysRest, int consecutiveDays,
                      @JsonProperty("appearances_last_7d") int appearancesLast7d,
                      @JsonProperty("pitches_last_3d") int pitchesLast3d) {

    public static Fatigue from(PitcherGameStats s) {
        return new Fatigue(SeasonStats.toDouble(s.getAvgPitchesPerPa()),
                s.getDaysRest() == null ? null : s.getDaysRest().intValue(),
                s.getConsecutiveDays(), s.getAppearancesLast7d(), s.getPitchesLast3d());
    }
}
