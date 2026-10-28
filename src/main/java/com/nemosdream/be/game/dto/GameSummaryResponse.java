package com.nemosdream.be.game.dto;

import com.nemosdream.be.game.entity.Game;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

/**
 * available_innings / high_leverage_innings 는 요청한 team 의 수비 반이닝 기준이다
 * (9회말 홈팀이 이기고 있으면 홈팀 쪽에는 9가 없다).
 */
public record GameSummaryResponse(
        String gameId,
        LocalDate date,
        String away,
        String home,
        FinalScore finalScore,
        List<Integer> availableInnings,
        List<Integer> highLeverageInnings
) {

    public record FinalScore(int away, int home) {
    }

    public static GameSummaryResponse of(Game game, String team) {
        return new GameSummaryResponse(
                game.getId(),
                game.getDate(),
                game.getAwayTeam(),
                game.getHomeTeam(),
                new FinalScore(game.getFinalAway(), game.getFinalHome()),
                toList(game.availableInningsOf(team)),
                toList(game.highLeverageInningsOf(team)));
    }

    private static List<Integer> toList(Short[] innings) {
        return Arrays.stream(innings).map(Short::intValue).toList();
    }
}
