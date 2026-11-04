package com.nemosdream.be.session.service;

import com.nemosdream.be.pitcher.dto.PitcherCardStats;
import com.nemosdream.be.pitcher.service.PitcherStatsService;
import com.nemosdream.be.session.domain.ActualPa;
import com.nemosdream.be.session.domain.Decision;
import com.nemosdream.be.session.domain.DivergedAt;
import com.nemosdream.be.session.domain.GameSession;
import com.nemosdream.be.session.domain.Half;
import com.nemosdream.be.session.domain.PlateAppearanceEngine;
import com.nemosdream.be.session.domain.SimState;
import com.nemosdream.be.session.dto.RepeatResponse;
import com.nemosdream.be.simulation.domain.Dice;
import com.nemosdream.be.simulation.domain.PaContext;
import com.nemosdream.be.simulation.domain.PaOutcome;
import com.nemosdream.be.simulation.properties.SimulationProperties;
import com.nemosdream.be.simulation.service.PaOutcomeModel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.TreeMap;
import org.springframework.stereotype.Service;

/**
 * "같은 선택으로 n회 반복" — 세션 시작 지점부터 경기 끝까지 주사위로 n번 다시 진행한다.
 *
 * <pre>
 * 내 팀 투수   반이닝마다 사용자가 고른 순서 그대로 (그 반이닝 k 번째 타석 = k 번째 선택, 타석이 더 길면 마지막 선택).
 *              사용자가 선택하지 않은 반이닝은 현재 투수 유지
 * 상대 투수    /advance-half 와 같은 규칙 (실제 같은 반이닝의 투수 순서)
 * 타석 결과    전부 주사위 (분기 전 실제 재생 없음)
 * </pre>
 * 같은 (투수, 타자, 아웃, 주자) 의 확률은 한 번만 계산해 재사용한다. 세션 상태는 바꾸지 않는다.
 */
@Service
public class RepeatService {

    private static final long SEED_SALT = 0x5DEECE66DL;
    private static final int MAX_PA_PER_GAME = 200;

    private final SessionService sessions;
    private final PaOutcomeModel model;
    private final PitcherStatsService pitcherStats;
    private final WinExpectancyCalculator winExpectancy;
    private final SimulationProperties properties;

    public RepeatService(SessionService sessions, PaOutcomeModel model, PitcherStatsService pitcherStats,
                         WinExpectancyCalculator winExpectancy, SimulationProperties properties) {
        this.sessions = sessions;
        this.model = model;
        this.pitcherStats = pitcherStats;
        this.winExpectancy = winExpectancy;
        this.properties = properties;
    }

    public RepeatResponse repeat(String sessionId, int n) {
        long started = System.nanoTime();
        GameSession session = sessions.get(sessionId);
        List<SimState> timeline = session.timeline();
        SimState start = timeline.getFirst();
        SimState current = timeline.getLast();
        boolean myAway = session.myTeamIsAway();

        Map<Integer, List<Integer>> choices = choicesByHalf(current.decisions());
        Map<Integer, List<ActualPa>> actualHalves = new HashMap<>();
        for (ActualPa p : session.actualPas()) {
            actualHalves.computeIfAbsent(order(p.inning(), p.half()), k -> new ArrayList<>()).add(p);
        }
        Map<String, Map<PaOutcome, Double>> probCache = new HashMap<>();
        Map<Integer, Double> pitchesCache = new HashMap<>();
        DivergedAt divergedAt = new DivergedAt(start.inning(), start.half(), start.nextActualPaIndex());
        SplittableRandom root = new SplittableRandom(session.seed() ^ SEED_SALT);

        int oppAtStart = myAway ? start.scoreHome() : start.scoreAway();
        int[] runsAllowed = new int[n];
        double[] finalWe = new double[n];
        int wins = 0;
        for (int run = 0; run < n; run++) {
            SplittableRandom random = root.split();
            SimState s = start;
            int halfOrder = -1;
            int k = 0;
            for (int pa = 0; !s.gameOver(); pa++) {
                if (pa >= MAX_PA_PER_GAME) {
                    throw new IllegalStateException("경기가 끝나지 않습니다: " + sessionId);
                }
                int o = order(s.inning(), s.half());
                if (o != halfOrder) {
                    halfOrder = o;
                    k = 0;
                }
                int pitcherId = session.isMyTeamFielding(s)
                        ? myPitcher(choices.get(o), k, s.currentPitcherId())
                        : HalfInningService.opponentPitcher(actualHalves.getOrDefault(o, List.of()), k, s.currentPitcherId());
                SimState state = s;
                Map<PaOutcome, Double> probs = probCache.computeIfAbsent(
                        pitcherId + ":" + s.currentBatterId() + ":" + s.outs() + ":" + s.bases().baseState(),
                        key -> model.probabilities(new PaContext(session.gameId(), pitcherId,
                                state.currentBatterId(), state.outs(), state.bases())));
                double pitchesPerPa = pitchesCache.computeIfAbsent(pitcherId, id -> pitchesPerPa(session.gameId(), id));
                s = PlateAppearanceEngine.simulate(s, pitcherId, Dice.roll(probs, random), pitchesPerPa, null, divergedAt)
                        .next();
                k++;
            }
            int my = myAway ? s.scoreAway() : s.scoreHome();
            int opp = myAway ? s.scoreHome() : s.scoreAway();
            runsAllowed[run] = opp - oppAtStart;
            finalWe[run] = winExpectancy.we(s, myAway);
            wins += my > opp ? 1 : 0;
        }

        int actualMy = myAway ? session.actualGame().finalAway() : session.actualGame().finalHome();
        int actualOpp = myAway ? session.actualGame().finalHome() : session.actualGame().finalAway();
        int actualRunsAllowed = actualOpp - oppAtStart;
        long better = Arrays.stream(runsAllowed).filter(r -> r < actualRunsAllowed).count();

        Map<String, Integer> histogram = new LinkedHashMap<>();
        TreeMap<Integer, Integer> counts = new TreeMap<>();
        for (int r : runsAllowed) {
            counts.merge(r, 1, Integer::sum);
        }
        counts.forEach((runs, c) -> histogram.put(Integer.toString(runs), c));

        double[] sortedRuns = Arrays.stream(runsAllowed).asDoubleStream().sorted().toArray();
        double[] sortedWe = Arrays.stream(finalWe).sorted().toArray();
        return new RepeatResponse(n,
                new RepeatResponse.RunsAllowed(histogram, quantile(sortedRuns, 0.5),
                        round4(Arrays.stream(runsAllowed).average().orElse(0))),
                new RepeatResponse.FinalWe(round4(quantile(sortedWe, 0.5)), round4(quantile(sortedWe, 0.1)),
                        round4(quantile(sortedWe, 0.9))),
                round4((double) wins / n), round4((double) better / n),
                new RepeatResponse.Actual(actualRunsAllowed,
                        actualMy > actualOpp ? "win" : actualMy < actualOpp ? "loss" : "tie"),
                (System.nanoTime() - started) / 1_000_000);
    }

    /** 사용자 선택을 반이닝별 순서로. 같은 반이닝 k 번째 내 팀 수비 타석의 투수 */
    static Map<Integer, List<Integer>> choicesByHalf(List<Decision> decisions) {
        Map<Integer, List<Integer>> byHalf = new HashMap<>();
        for (Decision d : decisions) {
            byHalf.computeIfAbsent(order(d.inning(), d.half()), k -> new ArrayList<>()).add(d.chosenPitcherId());
        }
        return byHalf;
    }

    static int myPitcher(List<Integer> halfChoices, int k, int current) {
        if (halfChoices == null || halfChoices.isEmpty()) {
            return current;
        }
        return halfChoices.get(Math.min(k, halfChoices.size() - 1));
    }

    private double pitchesPerPa(String gameId, int pitcherId) {
        PitcherCardStats card = pitcherStats.getCardStats(gameId, List.of(pitcherId)).get(pitcherId);
        Double avg = card == null ? null : card.fatigue().avgPitchesPerPa();
        return avg == null ? properties.defaultPitchesPerPa() : avg;
    }

    /** 정렬된 값의 분위수 (선형 보간) */
    static double quantile(double[] sorted, double q) {
        if (sorted.length == 0) {
            return 0;
        }
        double pos = q * (sorted.length - 1);
        int lo = (int) Math.floor(pos);
        int hi = Math.min(lo + 1, sorted.length - 1);
        return sorted[lo] + (sorted[hi] - sorted[lo]) * (pos - lo);
    }

    private static int order(int inning, Half half) {
        return inning * 2 + (half == Half.BOT ? 1 : 0);
    }

    private static double round4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}
