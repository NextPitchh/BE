package com.nemosdream.be.session.service;

import com.nemosdream.be.global.error.BusinessException;
import com.nemosdream.be.global.error.ErrorCode;
import com.nemosdream.be.pitcher.dto.PitcherCardStats;
import com.nemosdream.be.pitcher.service.PitcherStatsService;
import com.nemosdream.be.session.domain.Decision;
import com.nemosdream.be.session.domain.DivergedAt;
import com.nemosdream.be.session.domain.GameSession;
import com.nemosdream.be.session.domain.PlateAppearanceEngine;
import com.nemosdream.be.session.domain.PlateAppearanceEngine.ActualUnit;
import com.nemosdream.be.session.domain.PlateAppearanceEngine.Step;
import com.nemosdream.be.session.domain.SimState;
import com.nemosdream.be.session.dto.PaResultResponse;
import com.nemosdream.be.session.dto.SimulatePaResponse;
import com.nemosdream.be.simulation.domain.Dice;
import com.nemosdream.be.simulation.domain.PaContext;
import com.nemosdream.be.simulation.domain.PaOutcome;
import com.nemosdream.be.simulation.properties.SimulationProperties;
import com.nemosdream.be.simulation.service.PaOutcomeModel;
import com.nemosdream.be.weli.service.WeLiLookupService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SplittableRandom;
import org.springframework.stereotype.Service;

/**
 * 명세 §3-6 POST /sessions/{id}/simulate-pa — 내 팀 수비 타석 하나 진행.
 *
 * <pre>
 * 분기 전 + 실제 감독과 같은 투수  → 실제 기록 재생 (source "actual")
 * 그 외                            → 주사위 (source "simulated"). 처음 달라진 순간 is_diverged = true
 * </pre>
 * 실제 타석을 재생할 수 없는 경우(주자 아웃으로 이닝이 끝나 결과가 없는 타석 등, 약 0.3%)도 주사위로 진행하고 분기로 표시한다.
 */
@Service
public class PlateAppearanceService {

    /** 단계마다 seed 를 흩뜨리는 상수 (SplittableRandom 의 golden gamma) */
    private static final long SEED_STEP = 0x9E3779B97F4A7C15L;

    private final SessionService sessions;
    private final PaOutcomeModel model;
    private final PitcherStatsService pitcherStats;
    private final WeLiLookupService weLi;
    private final GameStateAssembler assembler;
    private final SimulationProperties properties;

    public PlateAppearanceService(SessionService sessions, PaOutcomeModel model, PitcherStatsService pitcherStats,
                                  WeLiLookupService weLi, GameStateAssembler assembler,
                                  SimulationProperties properties) {
        this.sessions = sessions;
        this.model = model;
        this.pitcherStats = pitcherStats;
        this.weLi = weLi;
        this.assembler = assembler;
        this.properties = properties;
    }

    public SimulatePaResponse simulate(String sessionId, int pitcherId) {
        GameSession session = sessions.get(sessionId);
        synchronized (session) {
            SimState s = session.current();
            validate(session, s, pitcherId);

            Optional<ActualUnit> unit = s.isDiverged() ? Optional.empty()
                    : PlateAppearanceEngine.actualUnit(session.actualPas(), s.nextActualPaIndex());
            Integer actualPitcherId = s.isDiverged() ? null
                    : unit.map(ActualUnit::startingPitcherId)
                          .orElse(session.actualPas().get(s.nextActualPaIndex()).pitcherId());
            Decision decision = new Decision(s.inning(), s.half(), s.outs(), s.bases().baseState(), pitcherId,
                    actualPitcherId, pitcherId != s.currentPitcherId());

            Map<PaOutcome, Double> probs = model.probabilities(
                    new PaContext(session.gameId(), pitcherId, s.currentBatterId(), s.outs(), s.bases()));

            Step step;
            if (unit.isPresent() && actualPitcherId == pitcherId) {
                step = PlateAppearanceEngine.replay(s, session.actualGame(), unit.get(), decision);
            } else {
                DivergedAt at = s.isDiverged() ? s.divergedAt()
                        : new DivergedAt(s.inning(), s.half(), s.nextActualPaIndex());
                PaOutcome rolled = Dice.roll(probs, new SplittableRandom(session.seed() + SEED_STEP * (session.stepCount() + 1)));
                step = PlateAppearanceEngine.simulate(s, pitcherId, rolled, pitchesPerPa(session.gameId(), pitcherId),
                        decision, at);
            }

            double weBefore = attackerWe(s);
            double weAfter = attackerWeAfter(s, step.next());
            session.advance(step.next());

            double delta = round4(weAfter - weBefore);
            return new SimulatePaResponse(new PaResultResponse(
                    step.outcome(), step.outcome().label(), step.description(),
                    round4(probs.getOrDefault(step.outcome(), 0.0)), rounded(probs), step.runs(),
                    delta, -delta + 0.0, step.actual() ? "actual" : "simulated",
                    assembler.toResponse(session)));
        }
    }

    private static void validate(GameSession session, SimState s, int pitcherId) {
        if (s.gameOver()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "경기가 끝났습니다.");
        }
        if (!session.isMyTeamFielding(s)) {
            throw new BusinessException(ErrorCode.INVALID_ROLE,
                    "공격 반이닝에서는 투수를 고를 수 없습니다. /advance-half 로 진행하세요.");
        }
        if (pitcherId == s.currentPitcherId()) {
            return;
        }
        if (!session.myBullpen().contains(pitcherId)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "이 경기 내 팀 명단에 없는 투수입니다: " + pitcherId);
        }
        if (s.pitcherDays().containsKey(pitcherId)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "이미 등판한 투수는 다시 나올 수 없습니다: " + pitcherId);
        }
    }

    private double pitchesPerPa(String gameId, int pitcherId) {
        PitcherCardStats card = pitcherStats.getCardStats(gameId, List.of(pitcherId)).get(pitcherId);
        Double avg = card == null ? null : card.fatigue().avgPitchesPerPa();
        return avg == null ? properties.defaultPitchesPerPa() : avg;
    }

    /** 타석 전 공격팀 관점 승률 */
    private double attackerWe(SimState s) {
        return weLi.lookup(s.inning(), s.half().code(), s.outs(), s.bases().baseState(), s.scoreDiffAttacker()).we();
    }

    /** 타석 후, 타석 전 공격팀 관점 승률. 반이닝이 바뀌었으면 새 공격팀 승률을 뒤집는다 */
    private double attackerWeAfter(SimState before, SimState after) {
        boolean attackerIsAway = before.half().awayBats();
        if (after.gameOver()) {
            int mine = attackerIsAway ? after.scoreAway() : after.scoreHome();
            int theirs = attackerIsAway ? after.scoreHome() : after.scoreAway();
            return mine > theirs ? 1.0 : mine < theirs ? 0.0 : 0.5;
        }
        double we = attackerWe(after);
        return after.half() == before.half() && after.inning() == before.inning() ? we : 1.0 - we;
    }

    /** 명세 예시처럼 결과 코드를 키로, 9종 순서대로 */
    private static Map<String, Double> rounded(Map<PaOutcome, Double> probs) {
        Map<String, Double> out = new LinkedHashMap<>();
        for (PaOutcome o : PaOutcome.values()) {
            out.put(o.code(), round4(probs.getOrDefault(o, 0.0)));
        }
        return out;
    }

    private static double round4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}
