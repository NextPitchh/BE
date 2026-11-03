package com.nemosdream.be.session.service;

import com.nemosdream.be.session.domain.ActualPa;
import com.nemosdream.be.session.domain.GameSession;
import com.nemosdream.be.session.domain.Half;
import com.nemosdream.be.session.domain.PaTrace;
import com.nemosdream.be.session.domain.SimState;
import com.nemosdream.be.session.dto.WeTraceResponse;
import com.nemosdream.be.session.dto.WeTraceResponse.HalfPoint;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 명세 §3-9 승률 곡선 + §3-10 레버리지 추이. 반이닝 단위로 모은다 (타석 단위는 곡선이 뭉개진다).
 * 진행 중인 반이닝 값이 바뀌므로 캐시하지 않는다.
 */
@Service
public class WeTraceService {

    private final SessionService sessions;
    private final WinExpectancyCalculator winExpectancy;

    public WeTraceService(SessionService sessions, WinExpectancyCalculator winExpectancy) {
        this.sessions = sessions;
        this.winExpectancy = winExpectancy;
    }

    public WeTraceResponse trace(String sessionId) {
        GameSession session = sessions.get(sessionId);
        List<SimState> timeline = session.timeline();
        SimState current = timeline.getLast();
        boolean forAway = session.myTeamIsAway();

        List<HalfPoint> simulated = current.isDiverged() ? simulated(timeline, forAway) : List.of();
        WeTraceResponse.DivergeAt divergeAt = current.isDiverged()
                ? new WeTraceResponse.DivergeAt(current.divergedAt().inning(), current.divergedAt().half()) : null;
        return new WeTraceResponse(session.myTeam(), actual(session.actualPas(), forAway), simulated, divergeAt,
                round4(winExpectancy.we(current, forAway)));
    }

    /** 실제 경기: 반이닝 종료 WE = 다음 반이닝 첫 타석 시작 WE, 마지막 반이닝은 최종 점수로 */
    private List<HalfPoint> actual(List<ActualPa> pas, boolean forAway) {
        Map<Integer, List<ActualPa>> halves = new LinkedHashMap<>();
        for (ActualPa p : pas) {
            halves.computeIfAbsent(order(p.inning(), p.half()), k -> new ArrayList<>()).add(p);
        }
        List<List<ActualPa>> list = new ArrayList<>(halves.values());
        List<HalfPoint> points = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            List<ActualPa> half = list.get(i);
            ActualPa first = half.getFirst();
            double awayWe;
            if (i + 1 < list.size()) {
                ActualPa n = list.get(i + 1).getFirst();
                awayWe = winExpectancy.awayWe(n.inning(), n.half(), n.outs(), n.baseState(), n.scoreAway(), n.scoreHome());
            } else {
                ActualPa last = half.getLast();
                int away = last.scoreAway() + (last.half().awayBats() ? last.runsScored() : 0);
                int home = last.scoreHome() + (last.half().awayBats() ? 0 : last.runsScored());
                awayWe = WinExpectancyCalculator.finalAwayWe(away, home);
            }
            double maxLi = half.stream()
                    .mapToDouble(p -> winExpectancy.leverageIndex(p.inning(), p.half(), p.outs(), p.baseState(),
                            p.scoreAway(), p.scoreHome()))
                    .max().orElse(0);
            points.add(point(first.inning(), first.half(), forAway ? awayWe : 1 - awayWe, maxLi));
        }
        return points;
    }

    /**
     * 시뮬레이션: 분기한 반이닝부터. 반이닝이 바뀐 상태의 WE 가 직전 반이닝의 종료 값이고,
     * 지금 반이닝은 지금 WE (경기가 끝났으면 최종 값).
     */
    private List<HalfPoint> simulated(List<SimState> timeline, boolean forAway) {
        SimState current = timeline.getLast();
        int from = order(current.divergedAt().inning(), current.divergedAt().half());
        Map<Integer, Double> maxLi = new LinkedHashMap<>();
        for (PaTrace t : current.trace()) {
            maxLi.merge(order(t.inning(), t.half()), t.leverageIndex(), Math::max);
        }

        List<HalfPoint> points = new ArrayList<>();
        for (int i = 1; i < timeline.size(); i++) {
            SimState prev = timeline.get(i - 1);
            SimState s = timeline.get(i);
            int prevOrder = order(prev.inning(), prev.half());
            boolean halfEnded = order(s.inning(), s.half()) != prevOrder && !(s.gameOver() && i == timeline.size() - 1);
            if (halfEnded && prevOrder >= from) {
                points.add(point(prev.inning(), prev.half(), winExpectancy.we(s, forAway),
                        maxLi.getOrDefault(prevOrder, 0.0)));
            }
        }
        int currentOrder = order(current.inning(), current.half());
        if (currentOrder >= from) {
            points.add(point(current.inning(), current.half(), winExpectancy.we(current, forAway),
                    maxLi.getOrDefault(currentOrder, 0.0)));
        }
        return points;
    }

    /** 반이닝 순서: 1회초 = 2, 1회말 = 3, ... */
    private static int order(int inning, Half half) {
        return inning * 2 + (half == Half.BOT ? 1 : 0);
    }

    private static HalfPoint point(int inning, Half half, double we, double li) {
        return new HalfPoint(inning, half, round4(we), round4(li));
    }

    private static double round4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}
