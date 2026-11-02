package com.nemosdream.be.simulation.ai;

import com.nemosdream.be.session.domain.SimState;
import java.util.Map;
import java.util.Set;

/** v6의 첫 투구 전 상태. 구수는 호출자가 정확히 추적한 완료 구수여야 한다. */
public record CatBoostV6State(String gameId, int inning, String half, int outCount, String baseState,
                              int homeScore, int awayScore, int pitcherId, int batterId,
                              int totalPitchCountBefore, int inningPitchCountBefore) {
    private static final Set<String> BASES = Set.of("0", "1", "2", "3", "12", "13", "23", "123");

    public CatBoostV6State {
        if (gameId == null || !gameId.matches("2025[0-9]{4}[A-Z]{4}[012]2025")
                || inning < 1 || inning > 9 || !("top".equals(half) || "bot".equals(half))
                || outCount < 0 || outCount > 2 || baseState == null || !BASES.contains(baseState)
                || homeScore < 0 || awayScore < 0 || pitcherId <= 0 || batterId <= 0
                || pitcherId > 999_999_999 || batterId > 999_999_999
                || totalPitchCountBefore < 0 || inningPitchCountBefore < 0
                || inningPitchCountBefore > totalPitchCountBefore) {
            throw new IllegalArgumentException("Invalid CatBoost v6 pre-pitch state");
        }
    }

    /** 현재 PitcherDay는 이닝 구수를 추적하지 않으므로 두 구수를 명시적으로 받는다. */
    public static CatBoostV6State from(String gameId, SimState s, int completedTotal, int completedInning) {
        if (s.gameOver()) throw new IllegalArgumentException("Game is over");
        return new CatBoostV6State(gameId, s.inning(), s.half().code(), s.outs(), s.bases().baseState(),
                s.scoreHome(), s.scoreAway(), s.currentPitcherId(), s.currentBatterId(), completedTotal, completedInning);
    }

    public Map<String, Object> asRequest() {
        return Map.of("state", Map.ofEntries(
                Map.entry("game_id", gameId), Map.entry("inning", inning), Map.entry("half", half),
                Map.entry("out_count", outCount), Map.entry("base_state", baseState),
                Map.entry("home_score", homeScore), Map.entry("away_score", awayScore),
                Map.entry("pitcher_id", pitcherId), Map.entry("batter_id", batterId),
                Map.entry("total_pitch_count_before", totalPitchCountBefore),
                Map.entry("inning_pitch_count_before", inningPitchCountBefore)));
    }
}
