package com.nemosdream.be.session.service;

import com.nemosdream.be.session.domain.GameSession;
import com.nemosdream.be.session.domain.SimState;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 교체 후보 점수 (명세 §5-1: 밴딧은 "교체할 경우 누구를 넣을지"만 점수를 낸다. 현재 투수 유지는 점수 없음).
 * 밴딧(윤서현 Flask /score)이 준비되면 구현을 하나 더 만들어 바꿔 끼운다.
 */
public interface CandidateScorer {

    /** 후보 ID → model_score (타석당 기대 WPA, 투수 관점). 모델이 없으면 empty */
    Optional<Map<Integer, Double>> score(GameSession session, SimState state, List<Integer> candidateIds);

    /** 응답 source: "precomputed" (실제 경기 상태) | "live" (분기 후) | "mock" */
    String source(SimState state);
}
