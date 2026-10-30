package com.nemosdream.be.session.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 한 경기의 실제 기록 (세션이 메모리에 들고 있으며 분기 전 재생에 쓴다).
 *
 * @param pas     실제 타석 전부 (pa_index 순, 리스트 index == pa_index)
 * @param lineups 타순 자리를 거쳐 간 타자 전부
 */
public record ActualGame(String awayTeam, String homeTeam, List<ActualPa> pas, List<LineupEntry> lineups) {

    public ActualGame {
        pas = List.copyOf(pas);
        lineups = List.copyOf(lineups);
    }

    /**
     * 실제 타석 t 시점의 라인업 (1~9번). 자리마다 t 까지 들어온 마지막 타자이고, 선발은 아직 타석 전이어도 라인업에 있다.
     * 대타는 실제로 나온 타석부터 보인다.
     */
    public List<Integer> lineupAt(boolean away, int t) {
        String team = away ? awayTeam : homeTeam;
        List<Integer> lineup = new ArrayList<>();
        for (int slot = 1; slot <= 9; slot++) {
            int s = slot;
            lineup.add(lineups.stream()
                    .filter(e -> e.teamCode().equals(team) && e.orderNo() == s && (e.seq() == 0 || e.firstPaIndex() <= t))
                    .max(Comparator.comparingInt(LineupEntry::seq))
                    .orElseThrow(() -> new IllegalStateException(team + " " + s + "번 타자 기록이 없습니다"))
                    .batterId());
        }
        return lineup;
    }
}
