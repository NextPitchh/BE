package com.nemosdream.be.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.nemosdream.be.session.domain.ActualPa;
import com.nemosdream.be.session.domain.BatterDay;
import com.nemosdream.be.session.domain.Decision;
import com.nemosdream.be.session.domain.DivergedAt;
import com.nemosdream.be.session.domain.Half;
import com.nemosdream.be.session.domain.Linescore;
import com.nemosdream.be.session.domain.PitcherDay;
import com.nemosdream.be.session.domain.PlateAppearanceEngine;
import com.nemosdream.be.session.domain.PlateAppearanceEngine.Step;
import com.nemosdream.be.session.domain.SimState;
import com.nemosdream.be.session.domain.TeamSide;
import com.nemosdream.be.simulation.domain.Bases;
import com.nemosdream.be.simulation.domain.PaOutcome;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class PlateAppearanceEngineTest {

    static final List<Integer> AWAY_LINEUP = IntStream.rangeClosed(51001, 51009).boxed().toList();
    static final List<Integer> HOME_LINEUP = IntStream.rangeClosed(52001, 52009).boxed().toList();
    static final int AWAY_PITCHER = 61001;
    static final int HOME_PITCHER = 60001;
    static final DivergedAt DIVERGED = new DivergedAt(9, Half.BOT, 70);

    @Test
    void 끝내기_안타면_바로_경기가_끝난다() {
        // 9회말 동점, 2아웃 3루, 원정 투수 수비
        SimState s = state(9, Half.BOT, 2, "3", 3, 3);
        Step step = simulate(s, PaOutcome.SINGLE);

        assertThat(step.runs()).isEqualTo(1);
        assertThat(step.next().gameOver()).isTrue();
        assertThat(step.next().scoreHome()).isEqualTo(4);
        assertThat(step.next().linescore().home().get(8)).isEqualTo(1);
    }

    @Test
    void 세_번째_아웃이면_반이닝이_바뀐다() {
        SimState s = state(5, Half.TOP, 2, "12", 1, 0);
        Step step = simulate(s, PaOutcome.STRIKEOUT);

        SimState n = step.next();
        assertThat(n.inning()).isEqualTo(5);
        assertThat(n.half()).isEqualTo(Half.BOT);
        assertThat(n.outs()).isZero();
        assertThat(n.bases()).isEqualTo(Bases.EMPTY);
        assertThat(n.linescore().home().get(4)).isZero();      // 시작한 반이닝은 0
        assertThat(n.gameOver()).isFalse();
        assertThat(n.batting().batterAt(n.batting().nextSlot())).isEqualTo(52001);
    }

    @Test
    void 말이_끝나면_다음_이닝_초로_간다() {
        Step step = simulate(state(6, Half.BOT, 2, "0", 1, 0), PaOutcome.GROUND_OUT);
        assertThat(step.next().inning()).isEqualTo(7);
        assertThat(step.next().half()).isEqualTo(Half.TOP);
    }

    @Test
    void 구회초가_끝났을_때_홈팀이_이기고_있으면_경기_끝() {
        Step step = simulate(state(9, Half.TOP, 2, "0", 2, 3), PaOutcome.FLY_OUT);
        assertThat(step.next().gameOver()).isTrue();
    }

    @Test
    void 구회말이_끝나면_동점이어도_경기_끝_연장_없음() {
        Step step = simulate(state(9, Half.BOT, 2, "0", 3, 3), PaOutcome.FLY_OUT);
        assertThat(step.next().gameOver()).isTrue();
        assertThat(step.next().scoreAway()).isEqualTo(step.next().scoreHome());
    }

    @Test
    void 주사위_타석은_투구수와_타자_기록과_결정을_쌓는다() {
        SimState s = state(7, Half.TOP, 0, "0", 1, 1);
        Step step = PlateAppearanceEngine.simulate(s, 60009, PaOutcome.DOUBLE, 3.8,
                new Decision(7, Half.TOP, 0, "0", 60009, 60002, true), new DivergedAt(7, Half.TOP, 50));

        SimState n = step.next();
        assertThat(n.pitcherDay(60009)).isEqualTo(new PitcherDay(1, 3.8));
        assertThat(n.currentPitcherId()).isEqualTo(60009);
        assertThat(n.batterDay(51001)).isEqualTo(new BatterDay(1, 1, 0, List.of("2B")));
        assertThat(n.batting().nextSlot()).isEqualTo(2);
        assertThat(n.away().hits()).isEqualTo(1);
        assertThat(n.bases().baseState()).isEqualTo("2");
        assertThat(n.decisions()).hasSize(1);
        assertThat(n.divergedAt()).isEqualTo(new DivergedAt(7, Half.TOP, 50));
        assertThat(n.nextActualPaIndex()).isNull();
    }

    @Test
    void 타석_도중_투수_교체는_두_행을_한_타석으로_재생한다() {
        // 행 0: 60001 이 던지다 교체(끝나지 않은 타석), 행 1: 같은 타자, 60002 가 마무리
        List<ActualPa> pas = List.of(
                actual(0, 51001, 60001, "1B", true),
                actual(1, 51001, 60002, "SO", false),
                actual(2, 51002, 60002, "GO", false));

        var unit = PlateAppearanceEngine.actualUnit(pas, 0).orElseThrow();
        assertThat(unit.rows()).hasSize(2);
        assertThat(unit.result()).isEqualTo(PaOutcome.STRIKEOUT);
        assertThat(unit.startingPitcherId()).isEqualTo(60001);
        assertThat(PlateAppearanceEngine.actualUnit(pas, 2).orElseThrow().rows()).hasSize(1);
    }

    @Test
    void 결과가_없는_실제_타석은_재생할_수_없다() {
        List<ActualPa> pas = List.of(actual(0, 51001, 60001, "UNK", true), actual(1, 52001, 61001, "SO", false));
        assertThat(PlateAppearanceEngine.actualUnit(pas, 0)).isEmpty();
        assertThat(PlateAppearanceEngine.actualUnit(pas, 5)).isEmpty();
    }

    private static Step simulate(SimState s, PaOutcome outcome) {
        int pitcher = s.currentPitcherId();
        return PlateAppearanceEngine.simulate(s, pitcher, outcome, 4.0,
                new Decision(s.inning(), s.half(), s.outs(), s.bases().baseState(), pitcher, null, false), DIVERGED);
    }

    private static SimState state(int inning, Half half, int outs, String bases, int away, int home) {
        Linescore ls = Linescore.empty().add(inning, half, 0);
        return new SimState(inning, half, outs, Bases.of(bases), away, home, ls,
                new TeamSide(AWAY_LINEUP, 1, AWAY_PITCHER, 0, 0), new TeamSide(HOME_LINEUP, 1, HOME_PITCHER, 0, 0),
                Map.of(), Map.of(), null, DIVERGED, false, List.of());
    }

    private static ActualPa actual(int index, int batter, int pitcher, String result, boolean partial) {
        return new ActualPa(index, 5, Half.TOP, 0, "0", 0, 0, batter, pitcher, 1, result, null, 0, 0, 3, partial, false);
    }
}
