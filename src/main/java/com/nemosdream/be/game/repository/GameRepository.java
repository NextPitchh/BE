package com.nemosdream.be.game.repository;

import com.nemosdream.be.game.entity.Game;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GameRepository extends JpaRepository<Game, String> {

    /** 기간 안에서 team 이 뛴 경기 (목록 제외 경기 빼고). opponent 가 null 이면 상대 무관. */
    @Query("""
            select g from Game g
            where g.date between :from and :to
              and g.excluded = false
              and (g.awayTeam = :team or g.homeTeam = :team)
              and (:opponent is null or g.awayTeam = :opponent or g.homeTeam = :opponent)
            order by g.date, g.id
            """)
    List<Game> findPlayable(@Param("from") LocalDate from, @Param("to") LocalDate to,
                            @Param("team") String team, @Param("opponent") String opponent);
}
