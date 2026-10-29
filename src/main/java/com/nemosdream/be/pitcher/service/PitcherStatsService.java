package com.nemosdream.be.pitcher.service;

import com.nemosdream.be.pitcher.dto.PitcherCardStats;
import com.nemosdream.be.pitcher.entity.PitcherGameStats;
import com.nemosdream.be.pitcher.repository.PitcherGameStatsRepository;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 경기 시작 시점의 투수 카드 기록 조회 (/recommend 후보·현재 투수 카드용). */
@Service
@Transactional(readOnly = true)
public class PitcherStatsService {

    private final PitcherGameStatsRepository repository;

    public PitcherStatsService(PitcherGameStatsRepository repository) {
        this.repository = repository;
    }

    /** 투수 ID → 카드 기록. 표에 없는 투수(그 경기 명단 밖)는 결과에 들어가지 않는다. */
    public Map<Integer, PitcherCardStats> getCardStats(String gameId, Collection<Integer> pitcherIds) {
        if (pitcherIds.isEmpty()) {
            return Map.of();
        }
        return repository.findByGame(gameId, pitcherIds).stream()
                .collect(Collectors.toUnmodifiableMap(PitcherGameStats::getPitcherId, PitcherCardStats::from));
    }
}
