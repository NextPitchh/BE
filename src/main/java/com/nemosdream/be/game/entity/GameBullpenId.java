package com.nemosdream.be.game.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;

@Embeddable
public record GameBullpenId(
        @Column(name = "game_id") String gameId,
        @Column(name = "team_code") String teamCode,
        @Column(name = "pitcher_id") int pitcherId
) implements Serializable {
}
