package com.nemosdream.be.session.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** 명세 §3-4 POST /sessions 요청. 시작 이닝은 5~9 (명세 §1-4). */
public record CreateSessionRequest(
        @Schema(example = "20250902HHKT02025")
        @NotNull @Pattern(regexp = "^\\d{8}[A-Z]{4}\\d{5}$", message = "game_id 형식이 올바르지 않습니다.") String gameId,
        @Schema(example = "HH")
        @NotNull @Pattern(regexp = "^[A-Z]{2}$", message = "my_team 은 팀 코드 2글자입니다 (예: HH).") String myTeam,
        @Schema(example = "7")
        @NotNull @Min(value = 5, message = "start_inning 은 5~9 입니다.") @Max(value = 9, message = "start_inning 은 5~9 입니다.")
        Integer startInning,
        @Schema(example = "play", description = "\"play\" | \"watch\". 없으면 play")
        @Pattern(regexp = "^(play|watch)$", message = "mode 는 play 또는 watch 입니다.") String mode
) {

    public String modeOrDefault() {
        return mode == null ? "play" : mode;
    }
}
