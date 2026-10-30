package com.nemosdream.be.simulation.domain;

/**
 * 주자 상황. baseState 표기는 DB(game_pa, we_li_table)와 같다: "0", "1", "2", "3", "12", "13", "23", "123".
 */
public record Bases(boolean first, boolean second, boolean third) {

    public static final Bases EMPTY = new Bases(false, false, false);

    public static Bases of(String baseState) {
        if (!baseState.matches("0|1|2|3|12|13|23|123")) {
            throw new IllegalArgumentException("잘못된 주자 상황: " + baseState);
        }
        return new Bases(baseState.contains("1"), baseState.contains("2"), baseState.contains("3"));
    }

    public String baseState() {
        String s = (first ? "1" : "") + (second ? "2" : "") + (third ? "3" : "");
        return s.isEmpty() ? "0" : s;
    }

    public int runnerCount() {
        return (first ? 1 : 0) + (second ? 1 : 0) + (third ? 1 : 0);
    }
}
