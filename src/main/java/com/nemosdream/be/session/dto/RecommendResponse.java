package com.nemosdream.be.session.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nemosdream.be.matchup.dto.MatchupLabel;
import com.nemosdream.be.pitcher.dto.SeasonStats;
import com.nemosdream.be.pitcher.dto.VsBatterHand;
import java.util.List;

/**
 * 명세 §3-5 POST /sessions/{id}/recommend.
 * candidates 는 교체 후보만 (현재 투수는 current_pitcher 로 분리). model_rank 오름차순이고 프론트는 재정렬하지 않는다.
 * 이미 등판한 투수도 availability "used" 로 넣되 model_score 는 넣지 않는다.
 * 밴딧 연결 전에는 source "mock", model_score·model_rank 가 null 이고 순서는 quality 내림차순 (임시).
 */
public record RecommendResponse(
        double leverageIndex,
        String leverageBand,
        int liPercentile,
        double liMax,
        String source,
        CurrentPitcher currentPitcher,
        List<Candidate> candidates,
        BullpenSummary bullpenSummary,
        String bullpenSource,
        ActualDecision actualDecision
) {

    /** 현재 투수: 점수 없음, 교체 판단용 정보만 */
    public record CurrentPitcher(PitcherResponse pitcher, ThisOuting thisOuting) {
    }

    /** 이번 등판 기록 (이 경기 전체). matchup_vs_batter = 현재 타자 기준 유리/불리 (근거 없으면 null) */
    public record ThisOuting(int pitches, int outs, int hitsAllowed, int walks, MatchupLabel matchupVsBatter) {
    }

    public record Candidate(CandidatePitcher pitcher) {
    }

    /**
     * 명세 §2-2 Pitcher + 후보 전용 필드. role 은 추가 필드: "bullpen" | "starter" | "unlisted".
     *
     * @param modelScore 타석당 기대 WPA (투수 관점, 높을수록 좋음). used 투수와 밴딧 연결 전에는 null
     * @param modelRank  available 후보 안에서의 순위 (1부터)
     */
    public record CandidatePitcher(
            int pitcherId,
            String name,
            @JsonProperty("throws") String throwingHand,
            String photoUrl,
            SeasonStats season,
            VsBatterHand vsBatterHand,
            PitcherResponse.Workload workload,
            String availability,
            String unavailableReason,
            Double modelScore,
            Integer modelRank,
            Integer qualityPercentile,
            MatchupLabel matchupVsBatter,
            String role
    ) {
    }

    public record BullpenSummary(int available, int used, int total) {
    }

    /** 실제 감독의 이번 타석 선택. 분기 후 null */
    public record ActualDecision(int pitcherId, String name, boolean wasChange) {
    }
}
