package com.nemosdream.be.session.service;

import com.nemosdream.be.global.error.BusinessException;
import com.nemosdream.be.global.error.ErrorCode;
import com.nemosdream.be.pitcher.dto.PitcherCardStats;
import com.nemosdream.be.pitcher.service.PitcherStatsService;
import com.nemosdream.be.player.entity.Player;
import com.nemosdream.be.player.repository.PlayerRepository;
import com.nemosdream.be.session.domain.ActualGame;
import com.nemosdream.be.session.domain.ActualPa;
import com.nemosdream.be.session.domain.GameSession;
import com.nemosdream.be.session.domain.PaTrace;
import com.nemosdream.be.session.domain.PaTraces;
import com.nemosdream.be.session.domain.PlateAppearanceEngine;
import com.nemosdream.be.session.domain.PlateAppearanceEngine.ActualUnit;
import com.nemosdream.be.session.domain.PlateAppearanceEngine.Step;
import com.nemosdream.be.session.domain.SimState;
import com.nemosdream.be.session.dto.AdvanceHalfResponse;
import com.nemosdream.be.simulation.domain.Dice;
import com.nemosdream.be.simulation.domain.PaContext;
import com.nemosdream.be.simulation.domain.PaOutcome;
import com.nemosdream.be.simulation.properties.SimulationProperties;
import com.nemosdream.be.simulation.service.PaOutcomeModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 명세 §3-7 POST /sessions/{id}/advance-half — 내 팀 공격 반이닝을 서버가 끝까지 진행한다.
 *
 * <pre>
 * 분기 전  실제 기록 재생 (결과가 없는 행은 타석 없이 지나간다)
 * 분기 후  상대 투수 = 실제 경기 같은 반이닝에서 같은 순번 타석의 투수 (실제 반이닝보다 길어지면 마지막 투수,
 *          실제에 없는 반이닝이면 현재 투수), 결과는 주사위
 * </pre>
 * 반이닝 전체가 되돌리기 한 단계다.
 */
@Service
public class HalfInningService {

    private static final long SEED_STEP = 0x9E3779B97F4A7C15L;
    private static final int MAX_PA_PER_HALF = 60;   // 무한 진행 방지

    private final SessionService sessions;
    private final PaOutcomeModel model;
    private final PitcherStatsService pitcherStats;
    private final PlayerRepository players;
    private final WinExpectancyCalculator winExpectancy;
    private final GameStateAssembler assembler;
    private final SimulationProperties properties;

    public HalfInningService(SessionService sessions, PaOutcomeModel model, PitcherStatsService pitcherStats,
                             PlayerRepository players, WinExpectancyCalculator winExpectancy,
                             GameStateAssembler assembler, SimulationProperties properties) {
        this.sessions = sessions;
        this.model = model;
        this.pitcherStats = pitcherStats;
        this.players = players;
        this.winExpectancy = winExpectancy;
        this.assembler = assembler;
        this.properties = properties;
    }

    public AdvanceHalfResponse advance(String sessionId) {
        GameSession session = sessions.get(sessionId);
        synchronized (session) {
            SimState start = session.current();
            if (start.gameOver()) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "경기가 끝났습니다.");
            }
            if (session.isMyTeamFielding(start)) {
                throw new BusinessException(ErrorCode.INVALID_ROLE,
                        "수비 반이닝은 자동 진행할 수 없습니다. /simulate-pa 로 투수를 골라 진행하세요.");
            }
            boolean simulated = start.isDiverged();
            ActualGame actual = session.actualGame();
            List<ActualPa> actualHalf = actual.pas().stream()
                    .filter(p -> p.inning() == start.inning() && p.half() == start.half())
                    .toList();
            SplittableRandom random = new SplittableRandom(session.seed() + SEED_STEP * (session.stepCount() + 1));

            List<Integer> batterIds = new ArrayList<>();
            List<PaOutcome> outcomes = new ArrayList<>();
            List<String> descriptions = new ArrayList<>();
            List<Double> weTrace = new ArrayList<>(List.of(round4(winExpectancy.myTeamWe(session, start))));
            int hits = 0;
            List<PaTrace> trace = new ArrayList<>(start.trace());

            SimState s = start;
            for (int pa = 0; sameHalf(s, start) && !s.gameOver(); pa++) {
                if (pa >= MAX_PA_PER_HALF) {
                    throw new IllegalStateException("반이닝이 끝나지 않습니다: " + sessionId);
                }
                Step step;
                if (simulated) {
                    int pitcherId = opponentPitcher(actualHalf, outcomes.size(), s.currentPitcherId());
                    Map<PaOutcome, Double> probs = model.probabilities(
                            new PaContext(session.gameId(), pitcherId, s.currentBatterId(), s.outs(), s.bases()));
                    step = PlateAppearanceEngine.simulate(s, pitcherId, Dice.roll(probs, random),
                            pitchesPerPa(session.gameId(), pitcherId), null, s.divergedAt());
                } else {
                    int t = s.nextActualPaIndex();
                    Optional<ActualUnit> unit = PlateAppearanceEngine.actualUnit(actual.pas(), t);
                    if (unit.isEmpty()) {
                        s = PlateAppearanceEngine.skipActualRow(s, actual, t);
                        continue;
                    }
                    step = PlateAppearanceEngine.replay(s, actual, unit.get(), null);
                }
                SimState before = s.withTrace(trace);
                trace.add(new PaTrace(s.inning(), s.half(), PaTraces.paInHalf(before), pitcherOf(step, s),
                        step.outcome().code(), winExpectancy.leverageIndex(s), winExpectancy.awayWe(s),
                        winExpectancy.awayWe(step.next())));
                batterIds.add(s.currentBatterId());
                outcomes.add(step.outcome());
                descriptions.add(step.description());
                hits += step.outcome().isHit() ? 1 : 0;
                s = step.next();
                weTrace.add(round4(winExpectancy.myTeamWe(session, s)));
            }

            int myRuns = session.myTeamIsAway() ? s.scoreAway() - start.scoreAway() : s.scoreHome() - start.scoreHome();
            session.advance(s.withTrace(trace));

            Map<Integer, Player> names = players.findAllById(batterIds).stream()
                    .collect(Collectors.toMap(Player::getId, Function.identity()));
            List<AdvanceHalfResponse.PaLogEntry> log = new ArrayList<>();
            for (int i = 0; i < outcomes.size(); i++) {
                Player p = names.get(batterIds.get(i));
                log.add(new AdvanceHalfResponse.PaLogEntry(batterIds.get(i), p == null ? null : p.getName(),
                        outcomes.get(i), descriptions.get(i)));
            }
            return new AdvanceHalfResponse(
                    new AdvanceHalfResponse.Summary(myRuns, hits, log, weTrace, simulated ? "simulated" : "actual"),
                    assembler.toResponse(session));
        }
    }

    /** 타석을 마친 수비 투수 (재생이면 실제, 도중 교체면 마무리 투수) */
    private static int pitcherOf(Step step, SimState before) {
        SimState n = step.next();
        boolean sameHalf = n.inning() == before.inning() && n.half() == before.half();
        return sameHalf || n.gameOver() ? n.fielding().pitcherId()
                : (before.half().awayBats() ? n.home() : n.away()).pitcherId();
    }

    /** 분기 후 상대 투수: 실제 같은 반이닝의 k 번째 타석 투수. 실제 반이닝이 없으면 지금 투수 */
    static int opponentPitcher(List<ActualPa> actualHalf, int k, int current) {
        if (actualHalf.isEmpty()) {
            return current;
        }
        return actualHalf.get(Math.min(k, actualHalf.size() - 1)).pitcherId();
    }

    private static boolean sameHalf(SimState s, SimState start) {
        return s.inning() == start.inning() && s.half() == start.half();
    }

    private double pitchesPerPa(String gameId, int pitcherId) {
        PitcherCardStats card = pitcherStats.getCardStats(gameId, List.of(pitcherId)).get(pitcherId);
        Double avg = card == null ? null : card.fatigue().avgPitchesPerPa();
        return avg == null ? properties.defaultPitchesPerPa() : avg;
    }

    private static double round4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}
