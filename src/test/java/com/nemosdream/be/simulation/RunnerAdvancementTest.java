package com.nemosdream.be.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nemosdream.be.simulation.domain.AdvanceResult;
import com.nemosdream.be.simulation.domain.Bases;
import com.nemosdream.be.simulation.domain.PaOutcome;
import com.nemosdream.be.simulation.domain.RunnerAdvancement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RunnerAdvancementTest {

    @ParameterizedTest(name = "{0}아웃 주자 {1} + {2} → {3}, {4}아웃 주자 {5}, {6}점")
    @CsvSource({
            // 아웃, 주자, 결과, 적용 결과, 아웃 후, 주자 후, 득점
            // 1루타: 1루 주자 → 3루, 2·3루 주자 홈인
            "0, 0,   1B,  1B,  0, 1,   0",
            "0, 1,   1B,  1B,  0, 13,  0",
            "0, 12,  1B,  1B,  0, 13,  1",
            "1, 13,  1B,  1B,  1, 13,  1",
            "2, 123, 1B,  1B,  2, 13,  2",
            "0, 23,  1B,  1B,  0, 1,   2",
            // 2루타: 주자 전원 홈인
            "0, 1,   2B,  2B,  0, 2,   1",
            "1, 123, 2B,  2B,  1, 2,   3",
            // 3루타, 홈런
            "0, 12,  3B,  3B,  0, 3,   2",
            "2, 123, HR,  HR,  2, 0,   4",
            "0, 0,   HR,  HR,  0, 0,   1",
            // 볼넷: 밀려나는 주자만
            "0, 2,   BB,  BB,  0, 12,  0",
            "0, 13,  BB,  BB,  0, 123, 0",
            "1, 12,  BB,  BB,  1, 123, 0",
            "2, 123, BB,  BB,  2, 123, 1",
            // 아웃: 주자 그대로, 희생플라이 없음
            "0, 3,   FO,  FO,  1, 3,   0",
            "1, 23,  GO,  GO,  2, 23,  0",
            "2, 123, SO,  SO,  3, 0,   0",
            // 병살: 0아웃이면 3루 주자 홈인, 2루 주자 그대로
            "0, 1,   GDP, GDP, 2, 0,   0",
            "0, 123, GDP, GDP, 2, 2,   1",
            "0, 13,  GDP, GDP, 2, 0,   1",
            "1, 123, GDP, GDP, 3, 0,   0",
            // 병살 불가능 → 땅볼 아웃
            "2, 1,   GDP, GO,  3, 0,   0",
            "0, 23,  GDP, GO,  1, 23,  0",
    })
    void 팀_규칙대로_진루한다(int outs, String bases, String outcome,
                       String applied, int outsAfter, String basesAfter, int runs) {
        AdvanceResult r = RunnerAdvancement.apply(outs, Bases.of(bases), PaOutcome.fromCode(outcome));

        assertThat(r.outcome().code()).isEqualTo(applied);
        assertThat(r.outs()).isEqualTo(outsAfter);
        assertThat(r.bases().baseState()).isEqualTo(basesAfter);
        assertThat(r.runsScored()).isEqualTo(runs);
    }

    @Test
    void 모든_상황에서_주자가_사라지거나_생기지_않는다() {
        String[] states = {"0", "1", "2", "3", "12", "13", "23", "123"};
        for (int outs = 0; outs <= 2; outs++) {
            for (String state : states) {
                for (PaOutcome outcome : PaOutcome.values()) {
                    Bases before = Bases.of(state);
                    AdvanceResult r = RunnerAdvancement.apply(outs, before, outcome);
                    String at = outs + "아웃 " + state + " " + outcome.code();

                    if (r.inningEnded()) {
                        assertThat(r.outs()).as(at).isEqualTo(3);
                        assertThat(r.bases()).as(at).isEqualTo(Bases.EMPTY);
                        assertThat(r.runsScored()).as(at).isZero();
                    } else {
                        // 타석 전 주자 + 타자 = 타석 후 주자 + 득점 + 이번 타석 아웃
                        assertThat(before.runnerCount() + 1)
                                .as(at).isEqualTo(r.bases().runnerCount() + r.runsScored() + (r.outs() - outs));
                    }
                }
            }
        }
    }

    @Test
    void 잘못된_입력은_예외() {
        assertThatThrownBy(() -> RunnerAdvancement.apply(3, Bases.EMPTY, PaOutcome.SINGLE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Bases.of("21")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PaOutcome.fromCode("SF")).isInstanceOf(IllegalArgumentException.class);
    }
}
