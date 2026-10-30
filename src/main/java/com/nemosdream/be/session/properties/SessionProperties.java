package com.nemosdream.be.session.properties;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 세션 설정. 값은 application.yaml 의 app.session. ttl = 마지막 요청 후 이 시간이 지나면 만료 (명세 §3-4: 30분). */
@ConfigurationProperties(prefix = "app.session")
public record SessionProperties(Duration ttl) {
}
