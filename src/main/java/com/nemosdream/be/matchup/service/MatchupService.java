package com.nemosdream.be.matchup.service;

import com.nemosdream.be.matchup.dto.MatchupLabel;
import com.nemosdream.be.matchup.dto.VsCurrentPitcher;
import com.nemosdream.be.matchup.entity.Matchup;
import com.nemosdream.be.matchup.properties.MatchupProperties;
import com.nemosdream.be.matchup.repository.MatchupRepository;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 경기 시작 시점의 투수 × 타자 상대 전적 조회와 유리/불리 판정. */
@Service
@Transactional(readOnly = true)
@EnableConfigurationProperties(MatchupProperties.class)
public class MatchupService {

    private final MatchupRepository repository;
    private final MatchupProperties properties;

    public MatchupService(MatchupRepository repository, MatchupProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    /**
     * 타자 카드용: 한 투수와 여러 타자의 상대 전적. 타자 ID → 전적, 첫 대결이면 Optional.empty() (응답에는 null).
     * 표에 없는 타자(이 경기 상대 타자가 아님)는 결과에 들어가지 않는다.
     */
    public Map<Integer, Optional<VsCurrentPitcher>> vsPitcher(String gameId, int pitcherId, Collection<Integer> batterIds) {
        Map<Integer, Optional<VsCurrentPitcher>> result = new HashMap<>();
        for (Matchup m : find(gameId, List.of(pitcherId), batterIds)) {
            result.put(m.getBatterId(), m.hasHistory() ? Optional.of(VsCurrentPitcher.from(m)) : Optional.empty());
        }
        return result;
    }

    /**
     * 교체 후보용: 여러 투수와 한 타자의 유리/불리. 투수 ID → 판정, 좌우 근거가 없으면 Optional.empty() (응답에는 null).
     * 표에 없는 투수(이 경기 명단 밖)는 결과에 들어가지 않는다.
     */
    public Map<Integer, Optional<MatchupLabel>> labelsVsBatter(String gameId, Collection<Integer> pitcherIds, int batterId) {
        Map<Integer, Optional<MatchupLabel>> result = new HashMap<>();
        for (Matchup m : find(gameId, pitcherIds, List.of(batterId))) {
            result.put(m.getPitcherId(), label(m));
        }
        return result;
    }

    /** 투수 관점: 상대 wOBA 가 리그 평균보다 margin 이상 낮으면 유리, 높으면 불리. */
    Optional<MatchupLabel> label(Matchup m) {
        if (!m.isHandednessResolved()) {
            return Optional.empty();
        }
        double diff = m.getWoba() - properties.leagueWoba();
        if (diff <= -properties.margin()) {
            return Optional.of(MatchupLabel.FAVORABLE);
        }
        if (diff >= properties.margin()) {
            return Optional.of(MatchupLabel.UNFAVORABLE);
        }
        return Optional.of(MatchupLabel.NEUTRAL);
    }

    private List<Matchup> find(String gameId, Collection<Integer> pitcherIds, Collection<Integer> batterIds) {
        if (pitcherIds.isEmpty() || batterIds.isEmpty()) {
            return List.of();
        }
        return repository.findByGame(gameId, pitcherIds, batterIds);
    }
}
