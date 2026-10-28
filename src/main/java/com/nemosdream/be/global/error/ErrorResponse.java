package com.nemosdream.be.global.error;

/**
 * 에러 응답 본문.
 *   { "error": { "code": "NO_AVAILABLE_PITCHER", "message": "...", "recoverable": false } }
 * 성공 응답은 감싸지 않고 DTO 를 그대로 내보낸다 (명세 예시가 전부 감싸지 않은 형태).
 */
public record ErrorResponse(ErrorDetail error) {

    public record ErrorDetail(String code, String message, boolean recoverable) {
    }

    public static ErrorResponse of(ErrorCode code) {
        return of(code, code.message());
    }

    public static ErrorResponse of(ErrorCode code, String message) {
        return new ErrorResponse(new ErrorDetail(code.name(), message, code.recoverable()));
    }
}
