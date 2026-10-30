package com.nemosdream.be.session.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** 명세 §3-6. 현재 투수를 유지할 때도 현재 투수의 pitcher_id 를 보낸다. */
public record SimulatePaRequest(
        @Schema(example = "61895") @NotNull Integer pitcherId
) {
}
