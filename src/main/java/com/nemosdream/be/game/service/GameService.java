package com.nemosdream.be.game.service;

import com.nemosdream.be.game.dto.GameSummaryResponse;
import com.nemosdream.be.game.repository.GameRepository;
import com.nemosdream.be.global.error.BusinessException;
import com.nemosdream.be.global.error.ErrorCode;
import com.nemosdream.be.team.service.TeamService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GameService {

    private final GameRepository gameRepository;
    private final TeamService teamService;

    public GameService(GameRepository gameRepository, TeamService teamService) {
        this.gameRepository = gameRepository;
        this.teamService = teamService;
    }

    public List<GameSummaryResponse> getGames(int season, String team, String opponent) {
        teamService.validateExists(team);
        if (opponent != null) {
            teamService.validateExists(opponent);
            if (opponent.equals(team)) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "team 과 opponent 가 같습니다.");
            }
        }
        LocalDate from = LocalDate.of(season, 1, 1);
        LocalDate to = LocalDate.of(season, 12, 31);
        return gameRepository.findPlayable(from, to, team, opponent).stream()
                .map(game -> GameSummaryResponse.of(game, team))
                .toList();
    }
}
