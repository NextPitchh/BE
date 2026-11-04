package com.nemosdream.be.session.service;

import com.nemosdream.be.game.entity.GameBullpen;
import com.nemosdream.be.player.entity.Player;
import com.nemosdream.be.player.repository.PlayerRepository;
import com.nemosdream.be.session.domain.ActualGame;
import com.nemosdream.be.session.domain.ActualPa;
import com.nemosdream.be.session.domain.CandidateRanking;
import com.nemosdream.be.session.domain.Decision;
import com.nemosdream.be.session.domain.GameSession;
import com.nemosdream.be.session.domain.Half;
import com.nemosdream.be.session.domain.PaTrace;
import com.nemosdream.be.session.domain.SimState;
import com.nemosdream.be.session.dto.ResultResponse;
import com.nemosdream.be.session.dto.ResultResponse.PitcherUsage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** 결과 화면: 시뮬레이션 vs 실제 경기 (세션 시작 지점부터) */
@Service
public class ResultService {

    private final SessionService sessions;
    private final PlayerRepository players;
    private final WinExpectancyCalculator winExpectancy;
    private final CandidateRankingService rankings;

    public ResultService(SessionService sessions, PlayerRepository players, WinExpectancyCalculator winExpectancy,
                         CandidateRankingService rankings) {
        this.sessions = sessions;
        this.players = players;
        this.winExpectancy = winExpectancy;
        this.rankings = rankings;
    }

    public ResultResponse result(String sessionId) {
        GameSession session = sessions.get(sessionId);
        List<SimState> timeline = session.timeline();
        SimState start = timeline.getFirst();
        SimState current = timeline.getLast();
        boolean myAway = session.myTeamIsAway();
        ActualGame actual = session.actualGame();

        List<PaTrace> myDefense = current.trace().stream().filter(t -> t.awayFielding() == myAway).toList();
        List<ActualPa> actualDefense = actual.pas().stream()
                .filter(p -> p.paIndex() >= start.nextActualPaIndex() && p.half().awayBats() != myAway)
                .toList();

        List<SimState> before = statesBeforeDecisions(timeline);
        List<Optional<CandidateRanking>> ranks = decisionRankings(session, before);

        Set<Integer> ids = new HashSet<>();
        myDefense.forEach(t -> ids.add(t.pitcherId()));
        actualDefense.forEach(p -> ids.add(p.pitcherId()));
        ranks.forEach(r -> r.map(CandidateRanking::top).ifPresent(ids::add));
        current.decisions().forEach(d -> {
            ids.add(d.chosenPitcherId());
            if (d.actualPitcherId() != null) {
                ids.add(d.actualPitcherId());
            }
        });
        Map<Integer, String> names = players.findAllById(ids).stream()
                .collect(Collectors.toMap(Player::getId, Player::getName));
        Function<Integer, String> name = names::get;

        return new ResultResponse(
                simulatedSide(start, current, myAway),
                actualSide(start, actual, myAway),
                simulatedUsage(myDefense, start, current, myAway, name),
                actualUsage(actual, actualDefense, myAway, name),
                decisions(current.decisions(), myDefense, ranks, myAway, name),
                agreement(current.decisions(), before, ranks),
                bullpenUsed(session, current, actual, myAway));
    }

    private ResultResponse.Side simulatedSide(SimState start, SimState s, boolean myAway) {
        int my = myAway ? s.scoreAway() : s.scoreHome();
        int opp = myAway ? s.scoreHome() : s.scoreAway();
        int oppAtStart = myAway ? start.scoreHome() : start.scoreAway();
        return new ResultResponse.Side(new ResultResponse.Score(my, opp), opp - oppAtStart,
                round4(winExpectancy.we(s, myAway)), s.gameOver() ? outcome(my, opp) : "in_progress");
    }

    /** 실제 경기는 연장까지 포함한 최종 점수 */
    private static ResultResponse.Side actualSide(SimState start, ActualGame actual, boolean myAway) {
        int my = myAway ? actual.finalAway() : actual.finalHome();
        int opp = myAway ? actual.finalHome() : actual.finalAway();
        int oppAtStart = myAway ? start.scoreHome() : start.scoreAway();
        double we = my > opp ? 1.0 : my < opp ? 0.0 : 0.5;
        return new ResultResponse.Side(new ResultResponse.Score(my, opp), opp - oppAtStart, we, outcome(my, opp));
    }

    /** 시뮬레이션 투수 운용: 내 팀 수비 타석을 연속된 같은 투수끼리 묶는다 */
    private static List<PitcherUsage> simulatedUsage(List<PaTrace> myDefense, SimState start, SimState current,
                                                     boolean myAway, Function<Integer, String> name) {
        List<PitcherUsage> usage = new ArrayList<>();
        int i = 0;
        while (i < myDefense.size()) {
            int j = i;
            double delta = 0;
            while (j < myDefense.size() && myDefense.get(j).pitcherId() == myDefense.get(i).pitcherId()) {
                PaTrace t = myDefense.get(j);
                delta += myAway ? t.awayWeAfter() - t.awayWeBefore() : t.awayWeBefore() - t.awayWeAfter();
                j++;
            }
            PaTrace first = myDefense.get(i);
            PaTrace last = myDefense.get(j - 1);
            int pid = first.pitcherId();
            int pitches = (int) Math.round(current.pitcherDay(pid).pitches() - start.pitcherDay(pid).pitches());
            usage.add(new PitcherUsage(pid, name.apply(pid),
                    new ResultResponse.PaPoint(first.inning(), first.half(), first.paInHalf()),
                    new ResultResponse.PaPoint(last.inning(), last.half(), last.paInHalf()),
                    round4(delta), pitches, round4(first.leverageIndex())));
            i = j;
        }
        return usage;
    }

    /** 실제 투수 운용: 세션 시작 지점 이후 실제 내 팀 수비 타석 */
    private List<PitcherUsage> actualUsage(ActualGame actual, List<ActualPa> defense, boolean myAway,
                                           Function<Integer, String> name) {
        List<ActualPa> pas = actual.pas();
        List<PitcherUsage> usage = new ArrayList<>();
        int i = 0;
        while (i < defense.size()) {
            int j = i;
            double delta = 0;
            while (j < defense.size() && defense.get(j).pitcherId() == defense.get(i).pitcherId()) {
                ActualPa p = defense.get(j);
                double before = awayWe(p);
                double after = p.paIndex() + 1 < pas.size() ? awayWe(pas.get(p.paIndex() + 1))
                        : WinExpectancyCalculator.finalAwayWe(actual.finalAway(), actual.finalHome());
                delta += myAway ? after - before : before - after;
                j++;
            }
            ActualPa first = defense.get(i);
            ActualPa last = defense.get(j - 1);
            usage.add(new PitcherUsage(first.pitcherId(), name.apply(first.pitcherId()),
                    point(actual, first), point(actual, last), round4(delta),
                    last.pitchCountBefore() + last.pitchesInPa() - first.pitchCountBefore(),
                    round4(winExpectancy.leverageIndex(first.inning(), first.half(), first.outs(), first.baseState(),
                            first.scoreAway(), first.scoreHome()))));
            i = j;
        }
        return usage;
    }

    /** 결정 i 직전 상태. /simulate-pa 만 결정을 하나씩 늘리므로, 결정 수가 늘어난 단계의 바로 앞 상태다 */
    static List<SimState> statesBeforeDecisions(List<SimState> timeline) {
        List<SimState> before = new ArrayList<>();
        for (int i = 1; i < timeline.size(); i++) {
            if (timeline.get(i).decisions().size() > timeline.get(i - 1).decisions().size()) {
                before.add(timeline.get(i - 1));
            }
        }
        return before;
    }

    /** 결정마다 그 시점 후보의 모델 순위. /recommend 때 저장한 순위를 쓰고, 없으면 그 상태로 모델을 부른다 */
    private List<Optional<CandidateRanking>> decisionRankings(GameSession session, List<SimState> before) {
        if (before.isEmpty()) {
            return List.of();
        }
        List<GameBullpen> listed = rankings.listed(session);
        return before.stream().map(s -> rankings.rankQuietly(session, s, listed)).toList();
    }

    /** 결정 = 내 팀 수비 타석. /simulate-pa 만 결정과 내 팀 수비 타석 기록을 하나씩 만들므로 순서대로 짝이 맞는다 */
    private static List<ResultResponse.DecisionRow> decisions(List<Decision> decisions, List<PaTrace> myDefense,
                                                              List<Optional<CandidateRanking>> ranks,
                                                              boolean myAway, Function<Integer, String> name) {
        List<ResultResponse.DecisionRow> rows = new ArrayList<>();
        for (int i = 0; i < decisions.size(); i++) {
            Decision d = decisions.get(i);
            PaTrace t = myDefense.get(i);
            Optional<CandidateRanking> r = rankAt(ranks, i);
            double delta = myAway ? t.awayWeAfter() - t.awayWeBefore() : t.awayWeBefore() - t.awayWeAfter();
            ResultResponse.PitcherRef top = r.map(CandidateRanking::top)
                    .map(id -> new ResultResponse.PitcherRef(id, name.apply(id))).orElse(null);
            ResultResponse.Choice actualChoice = d.actualPitcherId() == null ? null
                    : new ResultResponse.Choice(d.actualPitcherId(), name.apply(d.actualPitcherId()),
                            r.map(x -> x.rankOf(d.actualPitcherId())).orElse(null));
            rows.add(new ResultResponse.DecisionRow(d.inning(), d.half(), round4(t.leverageIndex()), top,
                    new ResultResponse.Choice(d.chosenPitcherId(), name.apply(d.chosenPitcherId()),
                            r.map(x -> x.rankOf(d.chosenPitcherId())).orElse(null)),
                    actualChoice, t.outcome(), round4(delta), d.changed()));
        }
        return rows;
    }

    /**
     * 순위는 교체한 결정에만 있다 (현재 투수 유지는 밴딧 후보가 아니다). 그래서 *_in_top3 의 분모는 교체 횟수
     * (my_changes, actual_changes) 다. 순위를 하나라도 못 구했으면 *_in_top3 는 null.
     */
    private static ResultResponse.Agreement agreement(List<Decision> decisions, List<SimState> before,
                                                      List<Optional<CandidateRanking>> ranks) {
        int same = (int) decisions.stream().filter(Decision::agreesWithActual).count();
        int myChanges = 0;
        int actualChanges = 0;
        int mineTop3 = 0;
        int actualTop3 = 0;
        boolean ranked = !decisions.isEmpty();
        for (int i = 0; i < decisions.size(); i++) {
            Decision d = decisions.get(i);
            Optional<CandidateRanking> r = rankAt(ranks, i);
            ranked &= r.isPresent();
            if (d.changed()) {
                myChanges++;
                mineTop3 += inTop3(r, d.chosenPitcherId());
            }
            boolean actualChanged = d.actualPitcherId() != null && i < before.size()
                    && d.actualPitcherId() != before.get(i).currentPitcherId();
            if (actualChanged) {
                actualChanges++;
                actualTop3 += inTop3(r, d.actualPitcherId());
            }
        }
        return new ResultResponse.Agreement(decisions.size(), ranked ? mineTop3 : null, ranked ? actualTop3 : null,
                same, myChanges, actualChanges);
    }

    private static Optional<CandidateRanking> rankAt(List<Optional<CandidateRanking>> ranks, int i) {
        return i < ranks.size() ? ranks.get(i) : Optional.empty();
    }

    private static int inTop3(Optional<CandidateRanking> r, int pitcherId) {
        Integer rank = r.map(x -> x.rankOf(pitcherId)).orElse(null);
        return rank != null && rank <= 3 ? 1 : 0;
    }

    /** 내 팀이 이 경기에서 쓴 투수 수 (세션 전 등판 포함) / 실제 / 명단 전체 */
    private static ResultResponse.BullpenUsed bullpenUsed(GameSession session, SimState current, ActualGame actual,
                                                          boolean myAway) {
        int mine = (int) current.pitcherDays().keySet().stream().filter(session.myBullpen()::contains).count();
        int actualUsed = (int) actual.pas().stream()
                .filter(p -> p.half().awayBats() != myAway)
                .map(ActualPa::pitcherId).distinct().count();
        return new ResultResponse.BullpenUsed(mine, actualUsed, session.myBullpen().size());
    }

    private double awayWe(ActualPa p) {
        return winExpectancy.awayWe(p.inning(), p.half(), p.outs(), p.baseState(), p.scoreAway(), p.scoreHome());
    }

    /** 실제 타석이 그 반이닝 몇 번째인가 */
    private static ResultResponse.PaPoint point(ActualGame actual, ActualPa p) {
        int firstOfHalf = p.paIndex();
        while (firstOfHalf > 0 && actual.pas().get(firstOfHalf - 1).sameHalfAs(p)) {
            firstOfHalf--;
        }
        return new ResultResponse.PaPoint(p.inning(), p.half(), p.paIndex() - firstOfHalf);
    }

    private static String outcome(int my, int opp) {
        return my > opp ? "win" : my < opp ? "loss" : "tie";
    }

    private static double round4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}
