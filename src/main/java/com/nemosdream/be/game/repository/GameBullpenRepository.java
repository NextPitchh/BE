package com.nemosdream.be.game.repository;

import com.nemosdream.be.game.entity.GameBullpen;
import com.nemosdream.be.game.entity.GameBullpenId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GameBullpenRepository extends JpaRepository<GameBullpen, GameBullpenId> {

    @Query("select b from GameBullpen b where b.id.gameId = :gameId and b.id.teamCode = :teamCode")
    List<GameBullpen> findByGameAndTeam(@Param("gameId") String gameId, @Param("teamCode") String teamCode);
}
