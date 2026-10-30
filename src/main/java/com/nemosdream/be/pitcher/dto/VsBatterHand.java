package com.nemosdream.be.pitcher.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nemosdream.be.global.util.Decimals;
import com.nemosdream.be.pitcher.entity.PitcherGameStats;

/** Pitcher.vs_batter_hand. 키는 대문자 "L" / "R" 이다. */
public record VsBatterHand(@JsonProperty("L") HandSplit left, @JsonProperty("R") HandSplit right) {

    public static VsBatterHand from(PitcherGameStats s) {
        return new VsBatterHand(
                new HandSplit(Decimals.round3(s.getVsLAvg()), Decimals.round3(s.getVsLWoba()),
                        Decimals.round3(s.getVsLIso()), s.getVsLPa()),
                new HandSplit(Decimals.round3(s.getVsRAvg()), Decimals.round3(s.getVsRWoba()),
                        Decimals.round3(s.getVsRIso()), s.getVsRPa()));
    }
}
