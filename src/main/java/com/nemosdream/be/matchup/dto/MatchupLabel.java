package com.nemosdream.be.matchup.dto;

import com.fasterxml.jackson.annotation.JsonValue;

/** 명세 §2-2 Pitcher.matchup_vs_batter. 투수 관점. */
public enum MatchupLabel {
    FAVORABLE("favorable"),
    NEUTRAL("neutral"),
    UNFAVORABLE("unfavorable");

    private final String value;

    MatchupLabel(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }
}
