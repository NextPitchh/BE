package com.nemosdream.be.matchup.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** matchup_vs_batter 판정 기준. 값은 application.yaml 의 app.matchup (회의 후 yaml 만 고치면 된다). */
@ConfigurationProperties(prefix = "app.matchup")
public record MatchupProperties(double leagueWoba, double margin) {
}
