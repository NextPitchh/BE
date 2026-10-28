package com.nemosdream.be.appconfig.controller;

import com.nemosdream.be.appconfig.properties.AppConfigProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Config", description = "앱 설정값")
@RestController
@EnableConfigurationProperties(AppConfigProperties.class)
public class AppConfigController {

    private final AppConfigProperties properties;

    public AppConfigController(AppConfigProperties properties) {
        this.properties = properties;
    }

    @Operation(summary = "앱 설정", description = "앱 기동 시 1회 호출. LI 기준값·밴드, 모델 점수 설명. "
            + "model.provisional 이 true 면 프론트가 \"임시 점수\" 배지를 띄운다.")
    @GetMapping("/config")
    public AppConfigProperties getConfig() {
        return properties;
    }
}
