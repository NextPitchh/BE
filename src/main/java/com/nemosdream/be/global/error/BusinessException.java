package com.nemosdream.be.global.error;

/**
 * 서비스 코드에서 의도적으로 던지는 예외. GlobalExceptionHandler 가 ErrorResponse 로 바꾼다.
 *   throw new BusinessException(ErrorCode.CANNOT_UNDO);
 *   throw new BusinessException(ErrorCode.GAME_NOT_FOUND, "game_id=" + gameId);   // 메시지 덮어쓰기
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.message());
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
