package com.nemosdream.be.pitcher.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * 경기 시작 시점의 투수 카드 정보. 모든 값은 그 경기 "전" 2025 시즌 기록이다 (quality 는 2016~ 통산).
 * 표본이 없으면 비율 값(whip, avg, woba ...)은 null 이다.
 */
@Entity
@Table(name = "pitcher_game_stats")
public class PitcherGameStats {

    @EmbeddedId
    private PitcherGameStatsId id;

    private short g;
    private short outs;
    private short so;
    private short bb;
    private short hbp;
    private short h;
    private short hr;
    private Float whip;

    @Column(name = "vs_l_pa")
    private short vsLPa;
    @Column(name = "vs_l_avg")
    private Float vsLAvg;
    @Column(name = "vs_l_woba")
    private Float vsLWoba;
    @Column(name = "vs_l_iso")
    private Float vsLIso;

    @Column(name = "vs_r_pa")
    private short vsRPa;
    @Column(name = "vs_r_avg")
    private Float vsRAvg;
    @Column(name = "vs_r_woba")
    private Float vsRWoba;
    @Column(name = "vs_r_iso")
    private Float vsRIso;

    @Column(name = "avg_pitches_per_pa")
    private Float avgPitchesPerPa;
    @Column(name = "days_rest")
    private Short daysRest;
    @Column(name = "consecutive_days")
    private short consecutiveDays;
    @Column(name = "appearances_last_7d")
    private short appearancesLast7d;
    @Column(name = "pitches_last_3d")
    private short pitchesLast3d;

    @Column(name = "quality_p")
    private Float qualityP;
    @Column(name = "quality_percentile")
    private Short qualityPercentile;

    protected PitcherGameStats() {
    }

    public PitcherGameStatsId getId() {
        return id;
    }

    public int getPitcherId() {
        return id.pitcherId();
    }

    public short getG() {
        return g;
    }

    public short getOuts() {
        return outs;
    }

    public short getSo() {
        return so;
    }

    public short getBb() {
        return bb;
    }

    public short getHbp() {
        return hbp;
    }

    public short getH() {
        return h;
    }

    public short getHr() {
        return hr;
    }

    public Float getWhip() {
        return whip;
    }

    public short getVsLPa() {
        return vsLPa;
    }

    public Float getVsLAvg() {
        return vsLAvg;
    }

    public Float getVsLWoba() {
        return vsLWoba;
    }

    public Float getVsLIso() {
        return vsLIso;
    }

    public short getVsRPa() {
        return vsRPa;
    }

    public Float getVsRAvg() {
        return vsRAvg;
    }

    public Float getVsRWoba() {
        return vsRWoba;
    }

    public Float getVsRIso() {
        return vsRIso;
    }

    public Float getAvgPitchesPerPa() {
        return avgPitchesPerPa;
    }

    public Short getDaysRest() {
        return daysRest;
    }

    public short getConsecutiveDays() {
        return consecutiveDays;
    }

    public short getAppearancesLast7d() {
        return appearancesLast7d;
    }

    public short getPitchesLast3d() {
        return pitchesLast3d;
    }

    public Float getQualityP() {
        return qualityP;
    }

    public Short getQualityPercentile() {
        return qualityPercentile;
    }
}
