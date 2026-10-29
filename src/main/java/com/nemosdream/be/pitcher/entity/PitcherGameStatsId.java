package com.nemosdream.be.pitcher.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;

@Embeddable
public record PitcherGameStatsId(
        @Column(name = "game_id") String gameId,
        @Column(name = "pitcher_id") int pitcherId
) implements Serializable {
}
