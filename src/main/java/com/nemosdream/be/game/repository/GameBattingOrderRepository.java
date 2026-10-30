package com.nemosdream.be.game.repository;

import com.nemosdream.be.game.entity.GameBattingOrder;
import com.nemosdream.be.game.entity.GameBattingOrderId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GameBattingOrderRepository extends JpaRepository<GameBattingOrder, GameBattingOrderId> {

    @Query("select o from GameBattingOrder o where o.id.gameId = :gameId order by o.id.teamCode, o.id.orderNo, o.id.seq")
    List<GameBattingOrder> findByGame(@Param("gameId") String gameId);
}
