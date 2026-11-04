package com.nemosdream.be.session.service;

import com.nemosdream.be.game.entity.GameBullpen;
import com.nemosdream.be.game.repository.GameBullpenRepository;
import com.nemosdream.be.session.domain.CandidateRanking;
import com.nemosdream.be.session.domain.GameSession;
import com.nemosdream.be.session.domain.SimState;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 교체 후보와 그 밴딧 순위. /recommend 와 결과 화면(decisions[].model_rank)이 같은 후보·같은 순위를 쓰도록 한곳에 둔다.
 *
 * <pre>
 * 후보 = 내 팀 경기 전 불펜 명단(is_listed) 중 현재 투수와 이미 등판한 투수를 뺀 투수
 * 순위 = CandidateScorer 점수 내림차순. 상태별로 세션에 저장해 두고 다시 부르지 않는다
 * </pre>
 */
@Service
public class CandidateRankingService {

    private static final Logger log = LoggerFactory.getLogger(CandidateRankingService.class);

    private final GameBullpenRepository bullpens;
    private final CandidateScorer scorer;

    public CandidateRankingService(GameBullpenRepository bullpens, CandidateScorer scorer) {
        this.bullpens = bullpens;
        this.scorer = scorer;
    }

    /** 내 팀 경기 전 불펜 명단 (is_listed) */
    public List<GameBullpen> listed(GameSession session) {
        return bullpens.findByGameAndTeam(session.gameId(), session.myTeam()).stream()
                .filter(GameBullpen::isListed)
                .toList();
    }

    /** 그 시점 교체 후보 (명단 순서 유지) */
    public static List<Integer> available(List<GameBullpen> listed, SimState s) {
        return listed.stream()
                .map(GameBullpen::getPitcherId)
                .filter(id -> id != s.currentPitcherId() && !s.pitcherDays().containsKey(id))
                .toList();
    }

    /** /recommend 용. 모델이 없으면 empty, 모델 오류는 그대로 던진다 */
    public Optional<CandidateRanking> rank(GameSession session, SimState s, List<Integer> available) {
        Optional<CandidateRanking> cached = session.cachedRanking(s);
        if (cached.isPresent()) {
            return cached;
        }
        Optional<CandidateRanking> ranking = scorer.score(session, s, available).map(CandidateRanking::of);
        ranking.ifPresent(r -> session.cacheRanking(s, r));
        return ranking;
    }

    /** 결과 화면용. 모델이 없거나 오류면 empty — 순위 칸만 비우고 화면은 그대로 보여준다 (저장하지 않아 다음 요청에 다시 시도) */
    public Optional<CandidateRanking> rankQuietly(GameSession session, SimState s, List<GameBullpen> listed) {
        List<Integer> available = available(listed, s);
        if (available.isEmpty()) {
            return Optional.empty();
        }
        try {
            return rank(session, s, available);
        } catch (RuntimeException e) {
            log.warn("결과 화면 모델 순위 실패 (session {}): {}", session.id(), e.getMessage());
            return Optional.empty();
        }
    }
}
