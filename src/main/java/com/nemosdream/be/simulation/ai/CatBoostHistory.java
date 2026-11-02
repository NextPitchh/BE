package com.nemosdream.be.simulation.ai;

/** 2024-01-01 미만의 동결 타수와 안타. */
public record CatBoostHistory(int batterPriorAb, int batterPriorHits, int matchupAb, int matchupHits) {
    public CatBoostHistory {
        if (batterPriorAb < 0 || batterPriorHits < 0 || batterPriorHits > batterPriorAb
                || matchupAb < 0 || matchupHits < 0 || matchupHits > matchupAb) {
            throw new IllegalArgumentException("Invalid frozen history");
        }
    }
}
