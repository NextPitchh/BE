package com.nemosdream.be.batter.service;

import com.nemosdream.be.batter.dto.BatterCardStats;
import com.nemosdream.be.batter.entity.BatterGameStats;
import com.nemosdream.be.batter.repository.BatterGameStatsRepository;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 경기 시작 시점의 타자 카드 기록 조회 (current_batter, upcoming_batters 용). */
@Service
@Transactional(readOnly = true)
public class BatterStatsService {

    private final BatterGameStatsRepository repository;

    public BatterStatsService(BatterGameStatsRepository repository) {
        this.repository = repository;
    }

    /** 타자 ID → 카드 기록. 표에 없는 타자(그 경기에 타석이 없었던 선수)는 결과에 들어가지 않는다. */
    public Map<Integer, BatterCardStats> getCardStats(String gameId, Collection<Integer> batterIds) {
        if (batterIds.isEmpty()) {
            return Map.of();
        }
        return repository.findByGame(gameId, batterIds).stream()
                .collect(Collectors.toUnmodifiableMap(BatterGameStats::getBatterId, BatterCardStats::from));
    }
}
