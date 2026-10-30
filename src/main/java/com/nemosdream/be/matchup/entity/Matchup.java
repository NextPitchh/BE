package com.nemosdream.be.matchup.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.regex.Pattern;

/**
 * 경기 시작 시점의 투수 × 타자 상대 전적 (그 경기 전 2016~ 통산).
 * woba, iso 는 표본이 작아 좌우 기준값 쪽으로 당긴 값이다. 첫 대결(pa = 0)이면 좌우 기준값 그대로.
 */
@Entity
@Table(name = "matchups")
public class Matchup {

    /** 투수 손(L/R/U) _ 타자 타석(L/R) 이 정해진 경우만 좌우 판정 근거가 있다. */
    private static final Pattern RESOLVED_HANDEDNESS = Pattern.compile("^[LRU]_[LR]$");

    @EmbeddedId
    private MatchupId id;

    @Column(name = "handedness_key")
    private String handednessKey;

    private short pa;
    private short ab;
    private short h;
    private Float avg;
    private float woba;
    private float iso;

    @Column(name = "base_woba")
    private float baseWoba;

    protected Matchup() {
    }

    public boolean hasHistory() {
        return pa > 0;
    }

    public boolean isHandednessResolved() {
        return RESOLVED_HANDEDNESS.matcher(handednessKey).matches();
    }

    public MatchupId getId() {
        return id;
    }

    public int getPitcherId() {
        return id.pitcherId();
    }

    public int getBatterId() {
        return id.batterId();
    }

    public String getHandednessKey() {
        return handednessKey;
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

    public Float getAvg() {
        return avg;
    }

    public float getWoba() {
        return woba;
    }

    public float getIso() {
        return iso;
    }

    public float getBaseWoba() {
        return baseWoba;
    }
}
