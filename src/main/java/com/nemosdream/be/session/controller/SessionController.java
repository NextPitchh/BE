package com.nemosdream.be.session.controller;

import com.nemosdream.be.global.error.ApiErrorCodes;
import com.nemosdream.be.global.error.ErrorCode;
import com.nemosdream.be.session.dto.CreateSessionRequest;
import com.nemosdream.be.session.dto.SessionStateResponse;
import com.nemosdream.be.session.dto.SimulatePaRequest;
import com.nemosdream.be.session.dto.SimulatePaResponse;
import com.nemosdream.be.session.service.PlateAppearanceService;
import com.nemosdream.be.session.service.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Session", description = "시뮬레이션 세션")
@RestController
public class SessionController {

    private final SessionService sessionService;
    private final PlateAppearanceService plateAppearanceService;

    public SessionController(SessionService sessionService, PlateAppearanceService plateAppearanceService) {
        this.sessionService = sessionService;
        this.plateAppearanceService = plateAppearanceService;
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

    @Operation(summary = "타석 진행",
            description = "내 팀 수비 타석 하나를 진행한다. 현재 투수를 유지할 때도 현재 투수의 pitcher_id 를 보낸다. "
                    + "분기 전이고 실제 감독과 같은 투수면 실제 기록을 재생하고(source: actual), "
                    + "다른 투수를 고르는 순간부터 주사위로 진행한다(source: simulated, is_diverged = true). "
                    + "3아웃이 되면 next_state 의 이닝·초말이 바뀌고 my_role 이 offense 가 된다.")
    @ApiErrorCodes({ErrorCode.SESSION_EXPIRED, ErrorCode.INVALID_ROLE})
    @PostMapping("/sessions/{sessionId}/simulate-pa")
    public SimulatePaResponse simulatePa(@PathVariable String sessionId,
                                         @Valid @RequestBody SimulatePaRequest request) {
        return plateAppearanceService.simulate(sessionId, request.pitcherId());
    }
}
