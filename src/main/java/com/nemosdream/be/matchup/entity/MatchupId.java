package com.nemosdream.be.matchup.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;

@Embeddable
public record MatchupId(
        @Column(name = "game_id") String gameId,
        @Column(name = "pitcher_id") int pitcherId,
        @Column(name = "batter_id") int batterId
) implements Serializable {
}
