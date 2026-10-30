package com.nemosdream.be.game.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * 실제 타석 기록 (game_pa). 상황 값(아웃·주자·점수)은 타석 시작 시점이다.
 * 끝나지 않은 타석(is_partial_pa)은 결과를 보여주지 않는다 — 같은 타자의 다음 행이 실제 결과다.
 */
@Entity
@Table(name = "game_pa")
public class GamePa {

    @EmbeddedId
    private GamePaId id;

    private short inning;
    private String half;

    @Column(name = "out_count")
    private short outCount;

    @Column(name = "base_state")
    private String baseState;

    @Column(name = "score_home")
    private short scoreHome;

    @Column(name = "score_away")
    private short scoreAway;

    @Column(name = "batter_id")
    private int batterId;

    @Column(name = "pitcher_id")
    private int pitcherId;

    @Column(name = "batting_order")
    private short battingOrder;

    private String result;

    @Column(name = "relay_text")
    private String relayText;

    @Column(name = "runs_scored")
    private short runsScored;

    @Column(name = "pitch_count_before")
    private short pitchCountBefore;

    @Column(name = "pitches_in_pa")
    private short pitchesInPa;

    @Column(name = "is_partial_pa")
    private boolean partialPa;

    @Column(name = "is_pitcher_change")
    private boolean pitcherChange;

    protected GamePa() {
    }

    public int getPaIndex() {
        return id.paIndex();
    }

    public short getInning() {
        return inning;
    }

    public String getHalf() {
        return half;
    }

    public short getOutCount() {
        return outCount;
    }

    public String getBaseState() {
        return baseState;
    }

    public short getScoreHome() {
        return scoreHome;
    }

    public short getScoreAway() {
        return scoreAway;
    }

    public int getBatterId() {
        return batterId;
    }

    public int getPitcherId() {
        return pitcherId;
    }

    public short getBattingOrder() {
        return battingOrder;
    }

    public String getResult() {
        return result;
    }

    public String getRelayText() {
        return relayText;
    }

    public short getRunsScored() {
        return runsScored;
    }

    public short getPitchCountBefore() {
        return pitchCountBefore;
    }

    public short getPitchesInPa() {
        return pitchesInPa;
    }

    public boolean isPartialPa() {
        return partialPa;
    }

    public boolean isPitcherChange() {
        return pitcherChange;
    }
}
