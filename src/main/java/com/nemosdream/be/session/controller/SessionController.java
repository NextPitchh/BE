package com.nemosdream.be.session.controller;

import com.nemosdream.be.global.error.ApiErrorCodes;
import com.nemosdream.be.global.error.ErrorCode;
import com.nemosdream.be.session.dto.CreateSessionRequest;
import com.nemosdream.be.session.dto.SessionStateResponse;
import com.nemosdream.be.session.service.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Session", description = "시뮬레이션 세션")
@RestController
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Operation(summary = "세션 시작",
            description = "선택한 경기·팀·시작 이닝으로 세션을 만든다. 상태는 시작 이닝에서 내 팀 수비 반이닝의 "
                    + "첫 실제 타석 직전이다. 세션은 마지막 요청 후 30분 동안 유지된다.")
    @ApiErrorCodes({ErrorCode.GAME_NOT_FOUND})
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/sessions")
    public SessionStateResponse create(@Valid @RequestBody CreateSessionRequest request) {
        return sessionService.create(request);
    }
}
