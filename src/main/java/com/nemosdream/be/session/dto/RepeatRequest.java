package com.nemosdream.be.session.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** POST /sessions/{id}/repeat 요청. n 이 없으면 100 */
public record RepeatRequest(
        @Schema(example = "100") @Min(value = 1, message = "n 은 1~500 입니다.") @Max(value = 500, message = "n 은 1~500 입니다.")
        Integer n
) {

    public int nOrDefault() {
        return n == null ? 100 : n;
    }
}
