package com.nemosdream.be.session.controller;

import com.nemosdream.be.global.error.ApiErrorCodes;
import com.nemosdream.be.global.error.ErrorCode;
import com.nemosdream.be.session.dto.AdvanceHalfResponse;
import com.nemosdream.be.session.dto.CreateSessionRequest;
import com.nemosdream.be.session.dto.RecommendResponse;
import com.nemosdream.be.session.dto.ResultResponse;
import com.nemosdream.be.session.dto.SessionStateResponse;
import com.nemosdream.be.session.dto.SimulatePaRequest;
import com.nemosdream.be.session.dto.SimulatePaResponse;
import com.nemosdream.be.session.dto.WeTraceResponse;
import com.nemosdream.be.session.service.HalfInningService;
import com.nemosdream.be.session.service.PlateAppearanceService;
import com.nemosdream.be.session.service.RecommendService;
import com.nemosdream.be.session.service.ResultService;
import com.nemosdream.be.session.service.SessionService;
import com.nemosdream.be.session.service.WeTraceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
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
    private final HalfInningService halfInningService;
    private final WeTraceService weTraceService;
    private final ResultService resultService;
    private final RecommendService recommendService;

    public SessionController(SessionService sessionService, PlateAppearanceService plateAppearanceService,
                             HalfInningService halfInningService, WeTraceService weTraceService,
                             ResultService resultService, RecommendService recommendService) {
        this.sessionService = sessionService;
        this.plateAppearanceService = plateAppearanceService;
        this.halfInningService = halfInningService;
        this.weTraceService = weTraceService;
        this.resultService = resultService;
        this.recommendService = recommendService;
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

    @Operation(summary = "공격 반이닝 자동 진행",
            description = "내 팀 공격 반이닝을 서버가 3아웃(또는 경기 종료)까지 진행한다. "
                    + "분기 전이면 실제 기록을 재생하고(summary.source: actual), 분기 후면 주사위로 진행한다(simulated). "
                    + "분기 후 상대 투수는 실제 경기 같은 반이닝의 투수 순서를 따른다. 반이닝 전체가 되돌리기 한 단계다.")
    @ApiErrorCodes({ErrorCode.SESSION_EXPIRED, ErrorCode.INVALID_ROLE})
    @PostMapping("/sessions/{sessionId}/advance-half")
    public AdvanceHalfResponse advanceHalf(@PathVariable String sessionId) {
        return halfInningService.advance(sessionId);
    }

    @Operation(summary = "되돌리기",
            description = "직전 상태로 돌아간다. 단위는 타석 1회이고, 자동 진행한 공격 반이닝은 통째로 되돌린다. "
                    + "분기 전으로 돌아가면 is_diverged 도 false 가 된다. 되돌린 뒤 같은 선택을 하면 같은 결과가 나온다.")
    @ApiErrorCodes({ErrorCode.SESSION_EXPIRED, ErrorCode.CANNOT_UNDO})
    @PostMapping("/sessions/{sessionId}/undo")
    public SessionStateResponse undo(@PathVariable String sessionId) {
        return sessionService.undo(sessionId);
    }

    @Operation(summary = "승률·레버리지 추이",
            description = "반이닝 단위 승률(my_team 관점, 반이닝 종료 시점)과 그 반이닝 최고 LI. "
                    + "actual 은 실제 경기 전체, simulated 는 분기한 반이닝부터 지금까지(분기 전이면 빈 배열). "
                    + "진행 중인 반이닝은 지금 값이다.")
    @ApiErrorCodes({ErrorCode.SESSION_EXPIRED})
    @GetMapping("/sessions/{sessionId}/we-trace")
    public WeTraceResponse weTrace(@PathVariable String sessionId) {
        return weTraceService.trace(sessionId);
    }

    @Operation(summary = "결과 화면",
            description = "세션 시작 지점부터의 시뮬레이션 vs 실제 경기 비교: 점수, 내준 점수, 최종 승률, 투수 운용, "
                    + "결정 비교 테이블(내 팀 수비 타석마다 한 줄), 실제 감독과 같은 선택 횟수, 불펜 사용 수. "
                    + "경기 도중에도 호출할 수 있고 그때 result 는 in_progress. 모델 순위 필드는 아직 null.")
    @ApiErrorCodes({ErrorCode.SESSION_EXPIRED})
    @GetMapping("/sessions/{sessionId}/result")
    public ResultResponse result(@PathVariable String sessionId) {
        return resultService.result(sessionId);
    }

    @Operation(summary = "교체 후보 추천",
            description = "지금 상황의 LI·밴드·리그 백분위와 교체 후보 목록. 현재 투수는 current_pitcher 로 분리하고 점수를 넣지 않는다. "
                    + "후보는 내 팀 경기 전 불펜 명단이고, 이미 등판한 투수는 availability: used 로 목록에 남긴다. "
                    + "미사용 후보는 B1 점수 내림차순이며, 사용한 투수의 점수는 null 이다.")
    @ApiErrorCodes({ErrorCode.SESSION_EXPIRED, ErrorCode.INVALID_ROLE, ErrorCode.NO_AVAILABLE_PITCHER, ErrorCode.MODEL_UNAVAILABLE})
    @PostMapping("/sessions/{sessionId}/recommend")
    public RecommendResponse recommend(@PathVariable String sessionId) {
        return recommendService.recommend(sessionId);
    }
}
