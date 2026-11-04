package com.nemosdream.be.session.service;

import com.nemosdream.be.session.domain.GameSession;
import com.nemosdream.be.session.domain.SimState;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** 밴딧 연결 전 임시 구현: 점수 없음, source "mock". 후보 순서는 RecommendService 가 quality 로 정한다 */
@Component
public class NoModelCandidateScorer implements CandidateScorer {

    @Override
    public Optional<Map<Integer, Double>> score(GameSession session, SimState state, List<Integer> candidateIds) {
        return Optional.empty();
    }

    @Override
    public String source(SimState state) {
        return "mock";
    }
}
