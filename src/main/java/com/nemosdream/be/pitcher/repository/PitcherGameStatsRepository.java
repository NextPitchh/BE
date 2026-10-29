package com.nemosdream.be.pitcher.repository;

import com.nemosdream.be.pitcher.entity.PitcherGameStats;
import com.nemosdream.be.pitcher.entity.PitcherGameStatsId;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PitcherGameStatsRepository extends JpaRepository<PitcherGameStats, PitcherGameStatsId> {

    @Query("select s from PitcherGameStats s where s.id.gameId = :gameId and s.id.pitcherId in :pitcherIds")
    List<PitcherGameStats> findByGame(@Param("gameId") String gameId, @Param("pitcherIds") Collection<Integer> pitcherIds);
}
