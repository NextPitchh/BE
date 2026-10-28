package com.nemosdream.be.game.controller;

import com.nemosdream.be.game.dto.GameSummaryResponse;
import com.nemosdream.be.game.service.GameService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Game", description = "경기")
@RestController
public class GameController {

    private static final String TEAM_CODE = "^[A-Z]{2}$";

    private final GameService gameService;

    public GameController(GameService gameService) {
        this.gameService = gameService;
    }

    @Operation(summary = "선택 가능한 경기 목록",
            description = "team 이 뛴 경기를 날짜 순으로 반환한다. 콜드게임 등 목록 제외 경기는 빠진다. "
                    + "available_innings / high_leverage_innings 는 team 의 수비 반이닝 기준.")
    @GetMapping("/games")
    public List<GameSummaryResponse> getGames(
            @Parameter(description = "시즌 (현재 2025 만 데이터 있음)", example = "2025")
            @RequestParam(defaultValue = "2025") @Min(2000) @Max(2100) int season,
            @Parameter(description = "내 팀 코드", example = "HH", required = true)
            @RequestParam @Pattern(regexp = TEAM_CODE, message = "team 은 팀 코드 2글자입니다 (예: HH).") String team,
            @Parameter(description = "상대 팀 코드. 없으면 전체", example = "KT")
            @RequestParam(required = false) @Pattern(regexp = TEAM_CODE, message = "opponent 는 팀 코드 2글자입니다 (예: KT).") String opponent) {
        return gameService.getGames(season, team, opponent);
    }
}
