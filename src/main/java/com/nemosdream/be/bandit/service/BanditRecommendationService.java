package com.nemosdream.be.bandit.service;

import com.nemosdream.be.bandit.client.BanditClient;
import com.nemosdream.be.bandit.dto.BanditPreparedRequest;
import com.nemosdream.be.bandit.dto.BanditRecommendationResponse;
import com.nemosdream.be.bandit.repository.BanditRepository;
import com.nemosdream.be.session.domain.GameSession;
import com.nemosdream.be.session.domain.SimState;
import com.nemosdream.be.session.service.CandidateScorer;
import com.nemosdream.be.session.service.SessionService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service
@Primary
public class BanditRecommendationService implements CandidateScorer {
    private static final Logger log=LoggerFactory.getLogger(BanditRecommendationService.class);
    private final SessionService sessions;
    private final BanditFeatureAssembler assembler;
    private final BanditClient client;

    public BanditRecommendationService(SessionService sessions, BanditFeatureAssembler assembler, BanditClient client) {
        this.sessions=sessions; this.assembler=assembler; this.client=client;
    }

    public BanditRecommendationResponse recommend(String sessionId) {
        var session=sessions.get(sessionId);
        var state=session.current();
        return recommend(session,state);
    }

    @Override
    public Optional<Map<Integer,Double>> score(GameSession session, SimState state, List<Integer> candidateIds) {
        var result=recommend(session,state);
        Map<Integer,Double> scores=result.rankings().stream()
                .collect(Collectors.toUnmodifiableMap(r->r.pitcherId(),r->r.predictedReward()));
        if (!scores.keySet().equals(Set.copyOf(candidateIds)))
            throw BanditRepository.dataError("추천 후보와 불펜 명단이 일치하지 않습니다.");
        return Optional.of(scores);
    }

    @Override
    public String source(SimState state) { return "live"; }

    private BanditRecommendationResponse recommend(GameSession session, SimState state) {
        BanditPreparedRequest prepared;
        try {
            prepared=assembler.assemble(session,state);
        } catch (DataAccessException e) {
            log.error("B1 데이터 조회 실패",e);
            throw BanditRepository.dataError("B1 데이터 테이블 또는 연결을 확인해 주세요.");
        }
        return client.recommend(prepared);
    }
}
