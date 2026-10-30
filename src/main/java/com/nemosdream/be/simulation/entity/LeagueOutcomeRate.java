package com.nemosdream.be.simulation.entity;

import com.nemosdream.be.simulation.domain.LeagueRates;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** log5 리그 기준 비율 (2024 시즌 한 행). */
@Entity
@Table(name = "league_outcome_rates")
public class LeagueOutcomeRate {

    @Id
    private String season;

    private int pa;

    @Column(name = "r_1b")
    private float single;
    @Column(name = "r_2b")
    private float dbl;
    @Column(name = "r_3b")
    private float triple;
    @Column(name = "r_hr")
    private float homeRun;
    @Column(name = "r_bb")
    private float walk;
    @Column(name = "r_so")
    private float strikeout;
    @Column(name = "r_fo")
    private float flyOut;
    @Column(name = "r_gb")
    private float groundBall;
    @Column(name = "dp_share")
    private float dpShare;

    protected LeagueOutcomeRate() {
    }

    public String getSeason() {
        return season;
    }

    public LeagueRates toLeagueRates() {
        return new LeagueRates(single, dbl, triple, homeRun, walk, strikeout, flyOut, groundBall, dpShare);
    }
}
