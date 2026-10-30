package com.nemosdream.be.simulation.domain;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 타석 결과 9종 (명세 §2-4). 프론트는 이 9종만 처리한다.
 * 희생플라이는 따로 두지 않는다 (팀 결정): 원본의 SF 는 플라이 아웃(FO)으로 합친다.
 */
public enum PaOutcome {
    SINGLE("1B", "1루타"),
    DOUBLE("2B", "2루타"),
    TRIPLE("3B", "3루타"),
    HOME_RUN("HR", "홈런"),
    WALK("BB", "볼넷"),
    STRIKEOUT("SO", "삼진"),
    GROUND_OUT("GO", "땅볼 아웃"),
    FLY_OUT("FO", "플라이 아웃"),
    DOUBLE_PLAY("GDP", "병살타");

    private final String code;
    private final String label;

    PaOutcome(String code, String label) {
        this.code = code;
        this.label = label;
    }

    @JsonValue
    public String code() {
        return code;
    }

    public String label() {
        return label;
    }

    public boolean isHit() {
        return this == SINGLE || this == DOUBLE || this == TRIPLE || this == HOME_RUN;
    }

    public static PaOutcome fromCode(String code) {
        for (PaOutcome o : values()) {
            if (o.code.equals(code)) {
                return o;
            }
        }
        throw new IllegalArgumentException("알 수 없는 타석 결과: " + code);
    }
}
