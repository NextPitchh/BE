package com.nemosdream.be.matchup.repository;

import com.nemosdream.be.matchup.entity.Matchup;
import com.nemosdream.be.matchup.entity.MatchupId;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MatchupRepository extends JpaRepository<Matchup, MatchupId> {

    @Query("""
            select m from Matchup m
            where m.id.gameId = :gameId and m.id.pitcherId in :pitcherIds and m.id.batterId in :batterIds
            """)
    List<Matchup> findByGame(@Param("gameId") String gameId,
                             @Param("pitcherIds") Collection<Integer> pitcherIds,
                             @Param("batterIds") Collection<Integer> batterIds);
}
