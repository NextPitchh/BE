package com.nemosdream.be.simulation.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 시뮬레이션 설정. 값은 application.yaml 의 app.simulation.
 *
 * @param log5K           선수 비율을 리그 쪽으로 당기는 강도 (가상 타석 수)
 * @param leagueSeason    log5 리그 기준 시즌 (league_outcome_rates.season)
 */
@ConfigurationProperties(prefix = "app.simulation")
public record SimulationProperties(double log5K, String leagueSeason) {
}
