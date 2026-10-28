package com.nemosdream.be.global.config;

import com.nemosdream.be.HealthController;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableConfigurationProperties(CorsProperties.class)
public class WebConfig implements WebMvcConfigurer {

    /** §3: 베이스 경로 /api/v1. 컨트롤러에는 /teams 처럼 뒷부분만 쓴다. */
    public static final String API_PREFIX = "/api/v1";
    private static final String BASE_PACKAGE = "com.nemosdream.be";

    private final CorsProperties cors;

    public WebConfig(CorsProperties cors) {
        this.cors = cors;
    }

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix(API_PREFIX, type -> type.isAnnotationPresent(RestController.class)
                && type.getPackageName().startsWith(BASE_PACKAGE)
                && type != HealthController.class);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping(API_PREFIX + "/**")
                .allowedOrigins(cors.allowedOrigins().toArray(String[]::new))
                .allowedMethods("GET", "POST", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}
