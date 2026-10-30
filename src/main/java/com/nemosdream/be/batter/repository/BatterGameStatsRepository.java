package com.nemosdream.be.batter.repository;

import com.nemosdream.be.batter.entity.BatterGameStats;
import com.nemosdream.be.batter.entity.BatterGameStatsId;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BatterGameStatsRepository extends JpaRepository<BatterGameStats, BatterGameStatsId> {

    @Query("select s from BatterGameStats s where s.id.gameId = :gameId and s.id.batterId in :batterIds")
    List<BatterGameStats> findByGame(@Param("gameId") String gameId, @Param("batterIds") Collection<Integer> batterIds);
}
