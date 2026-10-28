package com.nemosdream.be.global.config;

import com.nemosdream.be.global.error.ApiErrorCodes;
import com.nemosdream.be.global.error.ErrorCode;
import com.nemosdream.be.global.error.ErrorResponse;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI: /swagger-ui/index.html   API 문서(JSON): /v3/api-docs
 * 에러 응답은 @ApiErrorCodes 에 적은 코드를 상태코드별로 묶어 예시로 보여준다.
 */
@Configuration
public class OpenApiConfig {

    private static final List<ErrorCode> COMMON_ERRORS = List.of(ErrorCode.INVALID_REQUEST, ErrorCode.INTERNAL_ERROR);

    @Bean
    public OpenAPI openAPI() {
        // 에러 본문 스키마는 직접 정의한다 (중첩 record 를 자동 변환하면 안쪽 스키마가 빠지는 문제가 있음)
        Schema<?> errorDetail = new ObjectSchema()
                .addProperty("code", new StringSchema().example("CANNOT_UNDO"))
                .addProperty("message", new StringSchema().example("되돌릴 상태가 없습니다."))
                .addProperty("recoverable", new BooleanSchema().description("다시 요청하면 성공할 수 있는가 (재시도 UI)"));
        Components components = new Components()
                .addSchemas("ErrorResponse", new ObjectSchema().addProperty("error", errorDetail));
        return new OpenAPI()
                .info(new Info()
                        .title("K-Moneyball API")
                        .version("v1")
                        .description("""
                                KBO 투수 교체 시뮬레이터 백엔드
                                - 성공 응답은 감싸지 않고 DTO 를 그대로 반환한다.
                                - 실패 응답은 { "error": { "code", "message", "recoverable" } }.
                                - JSON 키는 snake_case.
                                """))
                .components(components);
    }

    /**
     * 실제 JSON 은 snake_case 인데 Swagger 는 자체 Jackson(2.x)으로 스키마를 만들어서 camelCase 로 보인다.
     * 같은 규칙을 Swagger 쪽에도 알려준다 (application.yml 의 spring.jackson.property-naming-strategy 와 짝).
     */
    @Bean
    public ModelResolver snakeCaseModelResolver() {
        return new ModelResolver(Json.mapper().copy().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE));
    }

    @Bean
    public OperationCustomizer errorResponseCustomizer() {
        return (operation, handlerMethod) -> {
            ApiErrorCodes annotation = handlerMethod.getMethodAnnotation(ApiErrorCodes.class);
            List<ErrorCode> codes = new ArrayList<>(COMMON_ERRORS);
            if (annotation != null) {
                codes.addAll(0, List.of(annotation.value()));
            }

            Map<Integer, List<ErrorCode>> byStatus = new LinkedHashMap<>();
            codes.stream().distinct().forEach(c -> byStatus.computeIfAbsent(c.status().value(), s -> new ArrayList<>()).add(c));

            ApiResponses responses = operation.getResponses();
            byStatus.forEach((status, list) -> {
                MediaType media = new MediaType().schema(new Schema<>().$ref("#/components/schemas/ErrorResponse"));
                list.forEach(c -> media.addExamples(c.name(), new Example()
                        .summary(c.message())
                        .value(ErrorResponse.of(c))));
                String description = String.join(", ", list.stream().map(Enum::name).toList());
                responses.addApiResponse(String.valueOf(status),
                        new ApiResponse().description(description).content(new Content().addMediaType("application/json", media)));
            });
            return operation;
        };
    }
}
