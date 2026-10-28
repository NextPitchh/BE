package com.nemosdream.be.global.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import com.nemosdream.be.HealthController;
import org.junit.jupiter.api.Test;
import com.nemosdream.be.global.config.WebConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = {GlobalExceptionHandlerTest.TestController.class, HealthController.class})
@Import({GlobalExceptionHandlerTest.TestController.class, HealthController.class, WebConfig.class, GlobalExceptionHandler.class})
class GlobalExceptionHandlerTest {

    @Autowired
    MockMvc mvc;

    @RestController
    static class TestController {
        record StartRequest(@NotBlank String gameId, @Min(5) @Max(9) int startInning) {
        }

        @GetMapping("/test/undo")
        void undo() {
            throw new BusinessException(ErrorCode.CANNOT_UNDO);
        }

        @GetMapping("/test/model")
        void model() {
            throw new BusinessException(ErrorCode.MODEL_UNAVAILABLE);
        }

        @GetMapping("/test/boom")
        void boom() {
            throw new IllegalStateException("unexpected");
        }

        @PostMapping("/test/start")
        StartRequest start(@Valid @RequestBody StartRequest req) {
            return req;
        }
    }

    @Test
    void 비즈니스_예외는_명세_형식으로_나간다() throws Exception {
        mvc.perform(get("/api/v1/test/undo"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CANNOT_UNDO"))
                .andExpect(jsonPath("$.error.message").value("되돌릴 상태가 없습니다."))
                .andExpect(jsonPath("$.error.recoverable").value(false));

        mvc.perform(get("/api/v1/test/model"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.recoverable").value(true));
    }

    @Test
    void 성공_응답은_감싸지_않고_snake_case() throws Exception {
        mvc.perform(post("/api/v1/test/start").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"game_id\":\"20250322HHKT02025\",\"start_inning\":7}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.game_id").value("20250322HHKT02025"))
                .andExpect(jsonPath("$.start_inning").value(7));
    }

    @Test
    void 검증_실패는_INVALID_REQUEST() throws Exception {
        mvc.perform(post("/api/v1/test/start").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"game_id\":\"20250322HHKT02025\",\"start_inning\":3}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        mvc.perform(post("/api/v1/test/start").contentType(MediaType.APPLICATION_JSON).content("{oops"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    void 없는_경로와_예상못한_예외() throws Exception {
        mvc.perform(get("/api/v1/nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));

        mvc.perform(get("/api/v1/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"));
    }

    @Test
    void 프론트_도메인은_CORS_허용() throws Exception {
        mvc.perform(options("/api/v1/test/undo")
                        .header("Origin", "https://nemosdream.vercel.app")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://nemosdream.vercel.app"));
    }

    @Test
    void health_는_api_v1_없이_그대로() throws Exception {
        // CD 배포 확인(cd.yml)이 /health 를 호출한다
        mvc.perform(get("/health")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/health")).andExpect(status().isNotFound());
    }
}
