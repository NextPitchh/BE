package com.nemosdream.be.session.domain;

import com.nemosdream.be.simulation.domain.Bases;
import java.util.List;
import java.util.Map;

/**
 * 경기 진행 상태 한 시점 (불변). 타석이 진행될 때마다 새 SimState 를 만들고, 되돌리기는 이전 값으로 돌아간다.
 *
 * @param nextActualPaIndex 분기 전: 다음 실제 타석 번호 (game_pa.pa_index). 분기 후 null
 * @param divergedAt        분기 전이면 null
 * @param decisions         지금까지 사용자의 투수 선택 (되돌리면 같이 되돌아간다)
 */
public record SimState(
        int inning,
        Half half,
        int outs,
        Bases bases,
        int scoreAway,
        int scoreHome,
        Linescore linescore,
        TeamSide away,
        TeamSide home,
        Map<Integer, PitcherDay> pitcherDays,
        Map<Integer, BatterDay> batterDays,
        Integer nextActualPaIndex,
        DivergedAt divergedAt,
        boolean gameOver,
        List<Decision> decisions
) {

    public SimState {
        pitcherDays = Map.copyOf(pitcherDays);
        batterDays = Map.copyOf(batterDays);
        decisions = List.copyOf(decisions);
    }

    public TeamSide batting() {
        return half.awayBats() ? away : home;
    }

    public TeamSide fielding() {
        return half.awayBats() ? home : away;
    }

    /** 공격팀 관점 점수차 (공격팀 - 수비팀) */
    public int scoreDiffAttacker() {
        return half.awayBats() ? scoreAway - scoreHome : scoreHome - scoreAway;
    }

    public boolean isDiverged() {
        return divergedAt != null;
    }

    public int currentBatterId() {
        TeamSide b = batting();
        return b.batterAt(b.nextSlot());
    }

    public int currentPitcherId() {
        return fielding().pitcherId();
    }

    public PitcherDay pitcherDay(int pitcherId) {
        return pitcherDays.getOrDefault(pitcherId, new PitcherDay(0, 0));
    }

    public BatterDay batterDay(int batterId) {
        return batterDays.getOrDefault(batterId, BatterDay.NONE);
    }
}
