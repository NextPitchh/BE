package com.nemosdream.be.session.service;

import com.nemosdream.be.session.domain.GameSession;
import com.nemosdream.be.session.domain.SimState;
import com.nemosdream.be.weli.service.WeLiLookupService;
import org.springframework.stereotype.Component;

/** 상태 → 승률. we_li_table 은 공격팀 관점이라 필요한 쪽으로 뒤집는다. 경기가 끝났으면 이기면 1, 지면 0, 동점 0.5. */
@Component
public class WinExpectancyCalculator {

    private final WeLiLookupService weLi;

    public WinExpectancyCalculator(WeLiLookupService weLi) {
        this.weLi = weLi;
    }

    /** 원정팀 관점 승률 */
    public double awayWe(SimState s) {
        if (s.gameOver()) {
            return s.scoreAway() > s.scoreHome() ? 1.0 : s.scoreAway() < s.scoreHome() ? 0.0 : 0.5;
        }
        double attacker = weLi.lookup(s.inning(), s.half().code(), s.outs(), s.bases().baseState(),
                s.scoreDiffAttacker()).we();
        return s.half().awayBats() ? attacker : 1.0 - attacker;
    }

    /** awayTeam 관점이면 그대로, 아니면 뒤집는다 */
    public double we(SimState s, boolean forAway) {
        double away = awayWe(s);
        return forAway ? away : 1.0 - away;
    }

    /** my_team 관점 승률 (GameState.win_expectancy, we_trace) */
    public double myTeamWe(GameSession session, SimState s) {
        return we(s, session.myTeamIsAway());
    }
}
