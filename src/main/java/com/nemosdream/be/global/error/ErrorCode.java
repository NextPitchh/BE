package com.nemosdream.be.global.error;

import org.springframework.http.HttpStatus;

/**
 * API 에러 코드.
 * 위 5개는 프론트가 code 값으로 분기 처리하므로 이름을 바꾸면 안 된다.
 * recoverable = 같은 요청을 다시 보내면 성공할 수 있는가 (프론트 재시도 UI 표시 여부).
 */
public enum ErrorCode {

    // 명세— 프론트 분기 대상
    SESSION_EXPIRED(HttpStatus.NOT_FOUND, "세션이 만료되었습니다. 처음부터 다시 시작해 주세요.", false),
    NO_AVAILABLE_PITCHER(HttpStatus.CONFLICT, "가용 불펜 투수가 없습니다.", false),
    INVALID_ROLE(HttpStatus.BAD_REQUEST, "공격 반이닝에서는 투수를 추천할 수 없습니다.", false),
    CANNOT_UNDO(HttpStatus.CONFLICT, "되돌릴 상태가 없습니다.", false),
    MODEL_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "추천 모델 서버가 응답하지 않습니다.", true),

    // 공통
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다.", false),
    GAME_NOT_FOUND(HttpStatus.NOT_FOUND, "경기를 찾을 수 없습니다.", false),
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 경로가 없습니다.", false),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다.", false),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.", true);

    private final HttpStatus status;
    private final String message;
    private final boolean recoverable;

    ErrorCode(HttpStatus status, String message, boolean recoverable) {
        this.status = status;
        this.message = message;
        this.recoverable = recoverable;
    }

    public HttpStatus status() {
        return status;
    }

    public String message() {
        return message;
    }

    public boolean recoverable() {
        return recoverable;
    }
}
