package com.nemosdream.be.game.repository;

import com.nemosdream.be.game.entity.GamePa;
import com.nemosdream.be.game.entity.GamePaId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GamePaRepository extends JpaRepository<GamePa, GamePaId> {

    @Query("select p from GamePa p where p.id.gameId = :gameId order by p.id.paIndex")
    List<GamePa> findByGame(@Param("gameId") String gameId);
}
