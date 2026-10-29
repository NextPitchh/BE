package com.nemosdream.be.pitcher.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nemosdream.be.pitcher.entity.PitcherGameStats;

/** Pitcher.vs_batter_hand. 키는 대문자 "L" / "R" 이다. */
public record VsBatterHand(@JsonProperty("L") HandSplit left, @JsonProperty("R") HandSplit right) {

    public static VsBatterHand from(PitcherGameStats s) {
        return new VsBatterHand(
                new HandSplit(SeasonStats.toDouble(s.getVsLAvg()), SeasonStats.toDouble(s.getVsLWoba()),
                        SeasonStats.toDouble(s.getVsLIso()), s.getVsLPa()),
                new HandSplit(SeasonStats.toDouble(s.getVsRAvg()), SeasonStats.toDouble(s.getVsRWoba()),
                        SeasonStats.toDouble(s.getVsRIso()), s.getVsRPa()));
    }
}
