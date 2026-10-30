package com.nemosdream.be.batter.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;

@Embeddable
public record BatterGameStatsId(
        @Column(name = "game_id") String gameId,
        @Column(name = "batter_id") int batterId
) implements Serializable {
}
