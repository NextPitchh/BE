package com.nemosdream.be.session.domain;

import com.fasterxml.jackson.annotation.JsonValue;

/** 초(원정 공격) / 말(홈 공격). DB·JSON 표기는 "top" / "bot". */
public enum Half {
    TOP("top"),
    BOT("bot");

    private final String code;

    Half(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    public static Half of(String code) {
        return switch (code) {
            case "top" -> TOP;
            case "bot" -> BOT;
            default -> throw new IllegalArgumentException("알 수 없는 초/말: " + code);
        };
    }

    /** 이 반이닝에 공격하는 쪽이 원정팀인가 */
    public boolean awayBats() {
        return this == TOP;
    }
}
