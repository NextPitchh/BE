package com.nemosdream.be.game.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;

@Embeddable
public record GameBattingOrderId(
        @Column(name = "game_id") String gameId,
        @Column(name = "team_code") String teamCode,
        @Column(name = "order_no") short orderNo,
        short seq
) implements Serializable {
}
