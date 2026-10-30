package com.nemosdream.be.batter.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * 경기 시작 시점의 타자 카드 정보. 모든 값은 그 경기 "전" 2025 시즌 기록이다.
 * 타수가 0 이면 비율 값(avg, obp ...)은 null 이다.
 */
@Entity
@Table(name = "batter_game_stats")
public class BatterGameStats {

    @EmbeddedId
    private BatterGameStatsId id;

    private short pa;
    private short ab;
    private short h;
    private short hr;
    private short bb;
    private short sf;
    private short tb;
    private Float avg;
    private Float obp;
    private Float slg;
    private Float ops;

    @Column(name = "recent_ab")
    private short recentAb;

    @Column(name = "recent_avg")
    private Float recentAvg;

    protected BatterGameStats() {
    }

    public BatterGameStatsId getId() {
        return id;
    }

    public int getBatterId() {
        return id.batterId();
    }

    public short getPa() {
        return pa;
    }

    public short getAb() {
        return ab;
    }

    public short getH() {
        return h;
    }

    public short getHr() {
        return hr;
    }

    public short getBb() {
        return bb;
    }

    public short getSf() {
        return sf;
    }

    public short getTb() {
        return tb;
    }

    public Float getAvg() {
        return avg;
    }

    public Float getObp() {
        return obp;
    }

    public Float getSlg() {
        return slg;
    }

    public Float getOps() {
        return ops;
    }

    public short getRecentAb() {
        return recentAb;
    }

    public Float getRecentAvg() {
        return recentAvg;
    }
}
