package com.nemosdream.be.game.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** 경기·팀별로 이 경기에 나올 수 있었던 투수 (game_bullpen). role: starter | bullpen | unlisted */
@Entity
@Table(name = "game_bullpen")
public class GameBullpen {

    @EmbeddedId
    private GameBullpenId id;

    private String role;

    @Column(name = "is_listed")
    private boolean listed;

    @Column(name = "first_pa_index")
    private Short firstPaIndex;

    protected GameBullpen() {
    }

    public String getTeamCode() {
        return id.teamCode();
    }

    public int getPitcherId() {
        return id.pitcherId();
    }

    public String getRole() {
        return role;
    }

    public boolean isListed() {
        return listed;
    }

    public Short getFirstPaIndex() {
        return firstPaIndex;
    }
}
