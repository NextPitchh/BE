package com.nemosdream.be.session.service;

import com.nemosdream.be.global.error.BusinessException;
import com.nemosdream.be.global.error.ErrorCode;
import com.nemosdream.be.session.domain.ActualGame;
import com.nemosdream.be.session.domain.ActualPa;
import com.nemosdream.be.session.domain.BatterDay;
import com.nemosdream.be.session.domain.Half;
import com.nemosdream.be.session.domain.Linescore;
import com.nemosdream.be.session.domain.PitcherDay;
import com.nemosdream.be.session.domain.SimState;
import com.nemosdream.be.session.domain.TeamSide;
import com.nemosdream.be.simulation.domain.Bases;
import java.util.ArrayList;
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
     * @param game 그 경기 실제 기록
     */
    public static SimState build(ActualGame game, boolean myTeamIsAway, int startInning) {
        List<ActualPa> pas = game.pas();
        Half myDefense = myTeamIsAway ? Half.BOT : Half.TOP;
        ActualPa first = pas.stream()
                .filter(p -> p.inning() == startInning && p.half() == myDefense)
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REQUEST,
                        startInning + "회에 내 팀 수비 기록이 없습니다."));
        int t0 = first.paIndex();
        List<ActualPa> before = pas.stream().filter(p -> p.paIndex() < t0).toList();

        Linescore linescore = Linescore.empty();
        int awayHits = 0, homeHits = 0, awayWalks = 0, homeWalks = 0;
        Map<Integer, BatterDay> batterDays = new HashMap<>();
        for (ActualPa p : before) {
            Half half = p.half();
            linescore = linescore.add(p.inning(), half, p.runsScored());
            if (p.partial()) {
                continue;
            }
            boolean hit = HITS.contains(p.result());
            boolean walk = "BB".equals(p.result());
            if (half.awayBats()) {
                awayHits += hit ? 1 : 0;
                awayWalks += walk ? 1 : 0;
            } else {
                homeHits += hit ? 1 : 0;
                homeWalks += walk ? 1 : 0;
            }
            batterDays.merge(p.batterId(), batterDayOf(p), InitialStateFactory::combine);
        }
        linescore = linescore.add(startInning, myDefense, 0);   // 진행 중인 반이닝은 0 부터

        TeamSide away = side(game, true, t0, before, Half.TOP, first, awayHits, awayWalks);
        TeamSide home = side(game, false, t0, before, Half.BOT, first, homeHits, homeWalks);

        return new SimState(first.inning(), myDefense, first.outs(), Bases.of(first.baseState()),
                first.scoreAway(), first.scoreHome(), linescore, away, home,
                pitcherDays(before), batterDays, t0, null, false, List.of());
    }

    /** 한 팀의 시점 t0 상태. battingHalf = 이 팀이 공격하는 반이닝 */
    private static TeamSide side(ActualGame game, boolean away, int t0, List<ActualPa> before,
                                 Half battingHalf, ActualPa first, int hits, int walks) {
        List<Integer> lineup = game.lineupAt(away, t0);
        String team = away ? game.awayTeam() : game.homeTeam();

        int nextSlot;
        if (first.half() == battingHalf) {
            nextSlot = first.battingOrder();
        } else {
            // 마지막 타석이 끝나지 않았으면(주자 아웃으로 이닝 종료) 그 타자가 다음 이닝 첫 타자
            nextSlot = last(before, p -> p.half() == battingHalf)
                    .map(p -> p.partial() ? p.battingOrder() : TeamSide.nextOf(p.battingOrder()))
                    .orElse(1);
        }

        Half fieldingHalf = battingHalf == Half.TOP ? Half.BOT : Half.TOP;
        int pitcherId = last(before, p -> p.half() == fieldingHalf)
                .map(ActualPa::pitcherId)
                .orElseThrow(() -> new IllegalStateException(team + " 의 이전 수비 기록이 없습니다"));
        return new TeamSide(lineup, nextSlot, pitcherId, hits, walks);
    }

    /** 투수별 상대 타자 수(끝난 타석)와 투구 수(마지막 타석 기준 누적) */
    private static Map<Integer, PitcherDay> pitcherDays(List<ActualPa> before) {
        Map<Integer, PitcherDay> days = new HashMap<>();
        for (ActualPa p : before) {
            PitcherDay prev = days.getOrDefault(p.pitcherId(), new PitcherDay(0, 0));
            days.put(p.pitcherId(), new PitcherDay(prev.paFaced() + (p.partial() ? 0 : 1),
                    p.pitchCountBefore() + p.pitchesInPa()));
        }
        return days;
    }

    private static BatterDay batterDayOf(ActualPa p) {
        String r = p.result();
        List<String> log = "UNK".equals(r) ? List.of() : List.of(r);
        return new BatterDay(AT_BATS.contains(r) ? 1 : 0, HITS.contains(r) ? 1 : 0, p.runsScored(), log);
    }

    private static BatterDay combine(BatterDay a, BatterDay b) {
        List<String> log = new ArrayList<>(a.log());
        log.addAll(b.log());
        return new BatterDay(a.ab() + b.ab(), a.h() + b.h(), a.rbi() + b.rbi(), log);
    }

    private static Optional<ActualPa> last(List<ActualPa> pas, Predicate<ActualPa> filter) {
        for (int i = pas.size() - 1; i >= 0; i--) {
            if (filter.test(pas.get(i))) {
                return Optional.of(pas.get(i));
            }
        }
        return Optional.empty();
    }
}
