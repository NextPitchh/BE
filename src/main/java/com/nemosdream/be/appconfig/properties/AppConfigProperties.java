package com.nemosdream.be.appconfig.properties;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * GET /config 응답 원본. 값은 application.yaml 의 app.client-config 에 둔다.
 * LI_max, 밴드 경계, provisional 은 논의 후 결과에 따라 yaml 만 고치면 된다.
 */
@ConfigurationProperties(prefix = "app.client-config")
public record AppConfigProperties(Leverage leverage, Model model, String liSourceNote) {

    public record Leverage(double leagueAvg, double max, List<Band> bands) {
    }

    public record Band(String label, double min, double max) {
    }

    public record Model(String scoreUnit, String description, boolean provisional) {
    }
}
