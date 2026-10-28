package com.nemosdream.be.game.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

/**
 * 이닝 배열은 팀별 "수비 반이닝" 기준이다. 원정팀 수비 = 말, 홈팀 수비 = 초.
 */
@Entity
@Table(name = "games")
public class Game {

    @Id
    @Column(name = "game_id")
    private String id;

    @Column(name = "game_date")
    private LocalDate date;

    @Column(name = "away_team")
    private String awayTeam;

    @Column(name = "home_team")
    private String homeTeam;

    @Column(name = "final_away")
    private short finalAway;

    @Column(name = "final_home")
    private short finalHome;

    @Column(name = "went_to_extras")
    private boolean wentToExtras;

    @Column(name = "is_cold_game")
    private boolean coldGame;

    @Column(name = "is_excluded")
    private boolean excluded;

    @Column(name = "available_innings_away")
    private Short[] availableInningsAway;

    @Column(name = "available_innings_home")
    private Short[] availableInningsHome;

    @Column(name = "high_leverage_innings_away")
    private Short[] highLeverageInningsAway;

    @Column(name = "high_leverage_innings_home")
    private Short[] highLeverageInningsHome;

    protected Game() {
    }

    public boolean isAway(String teamCode) {
        return awayTeam.equals(teamCode);
    }

    /** teamCode 가 수비하는 반이닝 중 시작 가능한 이닝 (5~9) */
    public Short[] availableInningsOf(String teamCode) {
        return isAway(teamCode) ? availableInningsAway : availableInningsHome;
    }

    /** teamCode 수비 반이닝 중 LI 가 높은 이닝 (데모용 "승부처 바로가기") */
    public Short[] highLeverageInningsOf(String teamCode) {
        return isAway(teamCode) ? highLeverageInningsAway : highLeverageInningsHome;
    }

    public String getId() {
        return id;
    }

    public LocalDate getDate() {
        return date;
    }

    public String getAwayTeam() {
        return awayTeam;
    }

    public String getHomeTeam() {
        return homeTeam;
    }

    public short getFinalAway() {
        return finalAway;
    }

    public short getFinalHome() {
        return finalHome;
    }

    public boolean isWentToExtras() {
        return wentToExtras;
    }

    public boolean isColdGame() {
        return coldGame;
    }

    public boolean isExcluded() {
        return excluded;
    }
}
