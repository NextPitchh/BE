package com.nemosdream.be.simulation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;

@Embeddable
public record OutcomeCountId(
        @Column(name = "game_id") String gameId,
        @Column(name = "player_id") int playerId,
        String role
) implements Serializable {

    public static final String BATTER = "B";
    public static final String PITCHER = "P";
}
