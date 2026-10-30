package com.nemosdream.be.session.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nemosdream.be.session.domain.Half;
import java.util.List;

/**
 * 명세 §2-1 GameState. 경기 진행 상태의 단일 소스.
 * score_diff = home - away, score_diff_attacker = 공격팀 - 수비팀 (프론트는 부호를 다시 바꾸지 않는다).
 * win_expectancy 는 my_team 관점이고 we_perspective 에 그 팀 코드를 적는다.
 */
public record GameStateResponse(
        String sessionId,
        String gameId,
        String date,
        int inning,
        Half half,
        int outCount,
        String baseState,
        Score score,
        int scoreDiff,
        int scoreDiffAttacker,
        String myTeam,
        String opponentTeam,
        String myRole,
        PitcherResponse currentPitcher,
        BatterResponse currentBatter,
        List<BatterResponse> upcomingBatters,
        double leverageIndex,
        double winExpectancy,
        String wePerspective,
        LinescoreResponse linescore,
        boolean isDiverged,
        DivergedAtResponse divergedAt,
        boolean isGameOver,
        boolean canUndo
) {

    public record Score(int home, int away) {
    }

    /** away/home 은 1~9회 득점, 시작 전 반이닝은 null. E(실책)는 원본에 없어 null */
    public record LinescoreResponse(List<Integer> away, List<Integer> home, Totals totals) {
    }

    public record Totals(TeamTotals away, TeamTotals home) {
    }

    public record TeamTotals(
            @JsonProperty("R") int runs,
            @JsonProperty("H") int hits,
            @JsonProperty("E") Integer errors,
            @JsonProperty("B") int walks
    ) {
    }

    public record DivergedAtResponse(int inning, Half half, int paIndex) {
    }
}
