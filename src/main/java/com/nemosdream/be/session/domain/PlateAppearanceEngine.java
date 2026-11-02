package com.nemosdream.be.session.domain;

import com.nemosdream.be.simulation.domain.AdvanceResult;
import com.nemosdream.be.simulation.domain.Bases;
import com.nemosdream.be.simulation.domain.PaOutcome;
import com.nemosdream.be.simulation.domain.RunnerAdvancement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 타석 하나를 진행해 다음 상태를 만든다 (DB 를 쓰지 않는 순수 계산).
 *
 * <pre>
 * 재생(replay)  분기 전 + 실제 감독과 같은 투수 → 실제 기록 그대로. 다음 상황(아웃·주자·점수)도 다음 실제 타석에서 가져와서
 *               타석 사이 도루·폭투 같은 실제 일까지 반영된다.
 * 주사위(simulate) 분기 후, 또는 실제와 다른 투수 → 뽑힌 결과 + 주자 진루 규칙
 * </pre>
 * 3아웃이면 반이닝을 바꾸고, 경기 종료(9회말 끝, 9회초 끝에 홈팀 리드, 9회말 이후 끝내기)를 판정한다. 연장은 없다.
 */
public final class PlateAppearanceEngine {

    private static final Set<String> AT_BATS = Set.of("1B", "2B", "3B", "HR", "SO", "GO", "FO", "GDP");
    private static final int LAST_INNING = 9;

    private PlateAppearanceEngine() {
    }

    /** 타석 진행 결과. actual = 실제 기록 재생인가 */
    public record Step(SimState next, PaOutcome outcome, int runs, String description, boolean actual) {
    }

    /**
     * 시점 t 에서 재생할 실제 타석 묶음. 타석 도중 투수가 바뀐 경우(끝나지 않은 행 + 같은 타자의 다음 행)는 두 행이 한 타석이다.
     *
     * @param rows   타석에 해당하는 실제 행 (마지막이 결과 행)
     * @param result 결과 행의 결과 (명세 9종)
     */
    public record ActualUnit(List<ActualPa> rows, PaOutcome result) {

        public ActualPa resultRow() {
            return rows.getLast();
        }

        /** 이 타석을 시작한 실제 투수 = 실제 감독의 선택 */
        public int startingPitcherId() {
            return rows.getFirst().pitcherId();
        }
    }

    /**
     * 실제 타석 t 를 재생할 수 있으면 그 묶음. 주자 아웃으로 이닝이 끝나 결과가 없는 타석(UNK) 등은 재생할 수 없다 (2025 약 0.3%).
     */
    public static Optional<ActualUnit> actualUnit(List<ActualPa> pas, int t) {
        if (t >= pas.size()) {
            return Optional.empty();
        }
        ActualPa row = pas.get(t);
        List<ActualPa> rows = new ArrayList<>(List.of(row));
        if (row.partial()) {
            if (t + 1 >= pas.size()) {
                return Optional.empty();
            }
            ActualPa next = pas.get(t + 1);
            if (next.partial() || !next.sameHalfAs(row) || next.batterId() != row.batterId()) {
                return Optional.empty();
            }
            rows.add(next);
        }
        return toOutcome(rows.getLast().result()).map(o -> new ActualUnit(rows, o));
    }

    /** 실제 결과 코드 → 명세 9종. 희생플라이는 플라이 아웃, 분류 안 된 OUT 은 플라이 아웃으로 본다 */
    static Optional<PaOutcome> toOutcome(String result) {
        return switch (result) {
            case "SF", "OUT" -> Optional.of(PaOutcome.FLY_OUT);
            case "UNK" -> Optional.empty();
            default -> Optional.of(PaOutcome.fromCode(result));
        };
    }

    /** 실제 기록 재생. 다음 상태는 다음 실제 타석의 시작 상황이고, 라인업도 그 시점 실제 라인업(대타 포함)이다 */
    public static Step replay(SimState s, ActualGame game, ActualUnit unit, Decision decision) {
        List<ActualPa> pas = game.pas();
        ActualPa r = unit.resultRow();
        PaOutcome outcome = unit.result();

        Map<Integer, PitcherDay> pitcherDays = new HashMap<>(s.pitcherDays());
        for (ActualPa row : unit.rows()) {
            PitcherDay prev = s.pitcherDay(row.pitcherId());
            pitcherDays.put(row.pitcherId(), new PitcherDay(prev.paFaced() + (row.partial() ? 0 : 1),
                    row.pitchCountBefore() + row.pitchesInPa()));
        }
        Map<Integer, BatterDay> batterDays = withBatterDay(s, r.batterId(), outcome, r.runsScored());

        int nextIndex = r.paIndex() + 1;
        ActualPa nextRow = nextIndex < pas.size() ? pas.get(nextIndex) : null;
        boolean sameHalf = nextRow != null && nextRow.sameHalfAs(r);

        int awayAfter = nextRow != null ? nextRow.scoreAway() : r.scoreAway() + (r.half().awayBats() ? r.runsScored() : 0);
        int homeAfter = nextRow != null ? nextRow.scoreHome() : r.scoreHome() + (r.half().awayBats() ? 0 : r.runsScored());
        int runs = s.half().awayBats() ? awayAfter - s.scoreAway() : homeAfter - s.scoreHome();

        boolean awayBats = s.half().awayBats();
        int lineupAt = nextRow != null ? nextIndex : r.paIndex();
        TeamSide batting = s.batting();
        int nextSlot = sameHalf ? nextRow.battingOrder() : TeamSide.nextOf(r.battingOrder());
        TeamSide newBatting = new TeamSide(game.lineupAt(awayBats, lineupAt), nextSlot, batting.pitcherId(),
                batting.hits() + (outcome.isHit() ? 1 : 0), batting.walks() + (outcome == PaOutcome.WALK ? 1 : 0));
        TeamSide fielding = s.fielding();
        // 반이닝이 바뀌면 다음 공격팀의 첫 타자도 실제 기록을 따른다 (주자 아웃으로 끝난 타석의 타자가 다시 나오는 경우 등)
        int fieldingNextSlot = !sameHalf && nextRow != null ? nextRow.battingOrder() : fielding.nextSlot();
        TeamSide newFielding = new TeamSide(game.lineupAt(!awayBats, lineupAt), fieldingNextSlot, r.pitcherId(),
                fielding.hits(), fielding.walks());

        Linescore linescore = s.linescore().add(s.inning(), s.half(), runs);
        List<Decision> decisions = append(s.decisions(), decision);
        TeamSide away = s.half().awayBats() ? newBatting : newFielding;
        TeamSide home = s.half().awayBats() ? newFielding : newBatting;
        String description = describe(r.relayText(), outcome);

        if (sameHalf) {
            SimState next = new SimState(s.inning(), s.half(), nextRow.outs(), Bases.of(nextRow.baseState()),
                    awayAfter, homeAfter, linescore, away, home, pitcherDays, batterDays,
                    nextIndex, null, false, decisions);
            return new Step(next, outcome, runs, description, true);
        }
        if (nextRow == null) {   // 실제 경기가 여기서 끝났다
            int outs = r.inningEnded() ? 3 : Math.min(3, s.outs() + 1);
            SimState next = new SimState(s.inning(), s.half(), outs, Bases.EMPTY, awayAfter, homeAfter, linescore,
                    away, home, pitcherDays, batterDays, null, null, true, decisions);
            return new Step(next, outcome, runs, description, true);
        }
        SimState next = new SimState(nextRow.inning(), nextRow.half(), nextRow.outs(), Bases.of(nextRow.baseState()),
                awayAfter, homeAfter, linescore.add(nextRow.inning(), nextRow.half(), 0), away, home,
                pitcherDays, batterDays, nextIndex, null, false, decisions);
        return new Step(next, outcome, runs, description, true);
    }

    /**
     * 주사위 결과로 진행. 진루는 RunnerAdvancement, 투구 수는 타석당 평균만큼 늘린다.
     *
     * @param divergedAt 이 타석부터 (또는 이미) 분기한 지점
     */
    public static Step simulate(SimState s, int pitcherId, PaOutcome rolled, double pitchesPerPa,
                                Decision decision, DivergedAt divergedAt) {
        AdvanceResult adv = RunnerAdvancement.apply(s.outs(), s.bases(), rolled);
        PaOutcome outcome = adv.outcome();
        int runs = adv.runsScored();
        int batterId = s.currentBatterId();

        int away = s.scoreAway() + (s.half().awayBats() ? runs : 0);
        int home = s.scoreHome() + (s.half().awayBats() ? 0 : runs);

        Map<Integer, PitcherDay> pitcherDays = new HashMap<>(s.pitcherDays());
        PitcherDay prev = s.pitcherDay(pitcherId);
        pitcherDays.put(pitcherId, new PitcherDay(prev.paFaced() + 1, prev.pitches() + pitchesPerPa));
        Map<Integer, BatterDay> batterDays = withBatterDay(s, batterId, outcome, runs);

        TeamSide batting = s.batting();
        TeamSide newBatting = new TeamSide(batting.lineup(), TeamSide.nextOf(batting.nextSlot()), batting.pitcherId(),
                batting.hits() + (outcome.isHit() ? 1 : 0), batting.walks() + (outcome == PaOutcome.WALK ? 1 : 0));
        TeamSide fielding = s.fielding();
        TeamSide newFielding = new TeamSide(fielding.lineup(), fielding.nextSlot(), pitcherId,
                fielding.hits(), fielding.walks());
        TeamSide awaySide = s.half().awayBats() ? newBatting : newFielding;
        TeamSide homeSide = s.half().awayBats() ? newFielding : newBatting;

        Linescore linescore = s.linescore().add(s.inning(), s.half(), runs);
        List<Decision> decisions = append(s.decisions(), decision);
        String description = outcome.label();

        boolean walkOff = s.half() == Half.BOT && s.inning() >= LAST_INNING && home > away;
        if (walkOff) {
            SimState next = new SimState(s.inning(), s.half(), adv.outs(), adv.bases(), away, home, linescore,
                    awaySide, homeSide, pitcherDays, batterDays, null, divergedAt, true, decisions);
            return new Step(next, outcome, runs, description, false);
        }
        if (!adv.inningEnded()) {
            SimState next = new SimState(s.inning(), s.half(), adv.outs(), adv.bases(), away, home, linescore,
                    awaySide, homeSide, pitcherDays, batterDays, null, divergedAt, false, decisions);
            return new Step(next, outcome, runs, description, false);
        }

        boolean gameOver = s.inning() >= LAST_INNING && (s.half() == Half.BOT || home > away);
        if (gameOver) {
            SimState next = new SimState(s.inning(), s.half(), 3, Bases.EMPTY, away, home, linescore,
                    awaySide, homeSide, pitcherDays, batterDays, null, divergedAt, true, decisions);
            return new Step(next, outcome, runs, description, false);
        }
        int inning = s.half() == Half.TOP ? s.inning() : s.inning() + 1;
        Half half = s.half() == Half.TOP ? Half.BOT : Half.TOP;
        SimState next = new SimState(inning, half, 0, Bases.EMPTY, away, home, linescore.add(inning, half, 0),
                awaySide, homeSide, pitcherDays, batterDays, null, divergedAt, false, decisions);
        return new Step(next, outcome, runs, description, false);
    }

    private static Map<Integer, BatterDay> withBatterDay(SimState s, int batterId, PaOutcome outcome, int runs) {
        BatterDay prev = s.batterDay(batterId);
        List<String> log = new ArrayList<>(prev.log());
        log.add(outcome.code());
        Map<Integer, BatterDay> days = new HashMap<>(s.batterDays());
        days.put(batterId, new BatterDay(prev.ab() + (AT_BATS.contains(outcome.code()) ? 1 : 0),
                prev.h() + (outcome.isHit() ? 1 : 0), prev.rbi() + runs, log));
        return days;
    }

    /** 중계 문구 "노시환 : 좌익수 플라이 아웃" → "좌익수 플라이 아웃". 없으면 결과 이름 */
    static String describe(String relayText, PaOutcome outcome) {
        if (relayText == null || relayText.isBlank()) {
            return outcome.label();
        }
        int colon = relayText.indexOf(" : ");
        return colon >= 0 ? relayText.substring(colon + 3).trim() : relayText.trim();
    }

    private static List<Decision> append(List<Decision> decisions, Decision decision) {
        List<Decision> all = new ArrayList<>(decisions);
        all.add(decision);
        return all;
    }
}
