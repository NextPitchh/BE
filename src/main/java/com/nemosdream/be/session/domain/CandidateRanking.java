package com.nemosdream.be.session.domain;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 한 시점의 교체 후보 밴딧 순위 (명세 §3-10 decisions[].model_rank: 그 시점 후보 집합 안에서의 순위).
 * 점수 내림차순, 같으면 pitcher_id 오름차순 (/recommend 의 candidates 순서와 같다).
 */
public record CandidateRanking(List<Integer> order, Map<Integer, Double> scores) {

    public CandidateRanking {
        order = List.copyOf(order);
        scores = Map.copyOf(scores);
    }

    public static CandidateRanking of(Map<Integer, Double> scores) {
        List<Integer> order = scores.keySet().stream()
                .sorted(Comparator.comparing((Integer id) -> scores.get(id)).reversed()
                        .thenComparing(Comparator.naturalOrder()))
                .toList();
        return new CandidateRanking(order, scores);
    }

    /** 모델 1위. 후보가 없으면 null */
    public Integer top() {
        return order.isEmpty() ? null : order.getFirst();
    }

    /** 1부터. 후보가 아니면 (현재 투수 유지, 이미 등판 등) null */
    public Integer rankOf(int pitcherId) {
        int i = order.indexOf(pitcherId);
        return i < 0 ? null : i + 1;
    }
}
