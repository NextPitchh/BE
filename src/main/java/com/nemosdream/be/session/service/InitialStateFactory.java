package com.nemosdream.be.session.service;

import com.nemosdream.be.game.entity.GameBattingOrder;
import com.nemosdream.be.game.entity.GamePa;
import com.nemosdream.be.global.error.BusinessException;
import com.nemosdream.be.global.error.ErrorCode;
import com.nemosdream.be.session.domain.BatterDay;
import com.nemosdream.be.session.domain.Half;
import com.nemosdream.be.session.domain.Linescore;
import com.nemosdream.be.session.domain.PitcherDay;
import com.nemosdream.be.session.domain.SimState;
import com.nemosdream.be.session.domain.TeamSide;
import com.nemosdream.be.simulation.domain.Bases;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 세션 시작 상태 = 시작 이닝에서 내 팀 수비 반이닝의 첫 실제 타석 직전.
 * 그 전까지의 실제 기록(점수, 이닝별 득점, 안타·볼넷, 타순, 투수, 오늘 기록)을 game_pa 에서 다시 만든다.
 */
public final class InitialStateFactory {

    private static final Set<String> HITS = Set.of("1B", "2B", "3B", "HR");
    private static final Set<String> AT_BATS = Set.of("1B", "2B", "3B", "HR", "SO", "GO", "FO", "GDP", "OUT");

    private InitialStateFactory() {
    }

    /**
     * @param pas    그 경기 실제 타석 전부 (pa_index 순)
     * @param orders 그 경기 타순 기록 전부
     */
    public static SimState build(String awayTeam, String homeTeam, boolean myTeamIsAway, int startInning,
                                 List<GamePa> pas, List<GameBattingOrder> orders) {
        Half myDefense = myTeamIsAway ? Half.BOT : Half.TOP;
        GamePa first = pas.stream()
                .filter(p -> p.getInning() == startInning && Half.of(p.getHalf()) == myDefense)
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REQUEST,
                        startInning + "회에 내 팀 수비 기록이 없습니다."));
        int t0 = first.getPaIndex();
        List<GamePa> before = pas.stream().filter(p -> p.getPaIndex() < t0).toList();

        Linescore linescore = Linescore.empty();
        int awayHits = 0, homeHits = 0, awayWalks = 0, homeWalks = 0;
        Map<Integer, BatterDay> batterDays = new HashMap<>();
        for (GamePa p : before) {
            Half half = Half.of(p.getHalf());
            linescore = linescore.add(p.getInning(), half, p.getRunsScored());
            if (p.isPartialPa()) {
                continue;
            }
            boolean hit = HITS.contains(p.getResult());
            boolean walk = "BB".equals(p.getResult());
            if (half.awayBats()) {
                awayHits += hit ? 1 : 0;
                awayWalks += walk ? 1 : 0;
            } else {
                homeHits += hit ? 1 : 0;
                homeWalks += walk ? 1 : 0;
            }
            batterDays.merge(p.getBatterId(), batterDayOf(p), InitialStateFactory::combine);
        }
        linescore = linescore.add(startInning, myDefense, 0);   // 진행 중인 반이닝은 0 부터

        TeamSide away = side(awayTeam, orders, t0, before, Half.TOP, first, awayHits, awayWalks);
        TeamSide home = side(homeTeam, orders, t0, before, Half.BOT, first, homeHits, homeWalks);

        return new SimState(first.getInning(), myDefense, first.getOutCount(), Bases.of(first.getBaseState()),
                first.getScoreAway(), first.getScoreHome(), linescore, away, home,
                pitcherDays(before), batterDays, t0, null, false);
    }

    /** 한 팀의 시점 t0 상태. battingHalf = 이 팀이 공격하는 반이닝 */
    private static TeamSide side(String team, List<GameBattingOrder> orders, int t0, List<GamePa> before,
                                 Half battingHalf, GamePa first, int hits, int walks) {
        List<Integer> lineup = new ArrayList<>();
        for (int slot = 1; slot <= 9; slot++) {
            int s = slot;
            lineup.add(orders.stream()
                    .filter(o -> o.getTeamCode().equals(team) && o.getOrderNo() == s
                            && (o.getSeq() == 0 || o.getFirstPaIndex() <= t0))   // 선발은 아직 타석 전이어도 라인업
                    .max(Comparator.comparingInt(GameBattingOrder::getSeq))
                    .orElseThrow(() -> new IllegalStateException(team + " " + s + "번 타자 기록이 없습니다"))
                    .getBatterId());
        }

        int nextSlot;
        if (Half.of(first.getHalf()) == battingHalf) {
            nextSlot = first.getBattingOrder();
        } else {
            nextSlot = last(before, p -> Half.of(p.getHalf()) == battingHalf)
                    .map(p -> TeamSide.nextOf(p.getBattingOrder()))
                    .orElse(1);
        }

        Half fieldingHalf = battingHalf == Half.TOP ? Half.BOT : Half.TOP;
        int pitcherId = last(before, p -> Half.of(p.getHalf()) == fieldingHalf)
                .map(GamePa::getPitcherId)
                .orElseThrow(() -> new IllegalStateException(team + " 의 이전 수비 기록이 없습니다"));
        return new TeamSide(lineup, nextSlot, pitcherId, hits, walks);
    }

    /** 투수별 상대 타자 수(끝난 타석)와 투구 수(마지막 타석 기준 누적) */
    private static Map<Integer, PitcherDay> pitcherDays(List<GamePa> before) {
        Map<Integer, PitcherDay> days = new HashMap<>();
        for (GamePa p : before) {
            PitcherDay prev = days.getOrDefault(p.getPitcherId(), new PitcherDay(0, 0));
            days.put(p.getPitcherId(), new PitcherDay(prev.paFaced() + (p.isPartialPa() ? 0 : 1),
                    p.getPitchCountBefore() + p.getPitchesInPa()));
        }
        return days;
    }

    private static BatterDay batterDayOf(GamePa p) {
        String r = p.getResult();
        List<String> log = "UNK".equals(r) ? List.of() : List.of(r);
        return new BatterDay(AT_BATS.contains(r) ? 1 : 0, HITS.contains(r) ? 1 : 0, p.getRunsScored(), log);
    }

    private static BatterDay combine(BatterDay a, BatterDay b) {
        List<String> log = new ArrayList<>(a.log());
        log.addAll(b.log());
        return new BatterDay(a.ab() + b.ab(), a.h() + b.h(), a.rbi() + b.rbi(), log);
    }

    private static Optional<GamePa> last(List<GamePa> pas, Predicate<GamePa> filter) {
        for (int i = pas.size() - 1; i >= 0; i--) {
            if (filter.test(pas.get(i))) {
                return Optional.of(pas.get(i));
            }
        }
        return Optional.empty();
    }
}
