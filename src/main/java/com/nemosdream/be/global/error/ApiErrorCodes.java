package com.nemosdream.be.global.error;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 이 API 가 낼 수 있는 에러 코드. Swagger 문서에 상태코드별 예시로 표시된다 (OpenApiConfig).
 *   @ApiErrorCodes({ErrorCode.SESSION_EXPIRED, ErrorCode.CANNOT_UNDO})
 * INVALID_REQUEST, INTERNAL_ERROR 는 모든 API 에 자동으로 붙으므로 적지 않는다.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiErrorCodes {
    ErrorCode[] value();
}
