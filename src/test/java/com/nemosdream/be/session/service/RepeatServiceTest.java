package com.nemosdream.be.session.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.nemosdream.be.session.domain.Decision;
import com.nemosdream.be.session.domain.Half;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RepeatServiceTest {

    @Test
    void 반이닝마다_사용자가_고른_순서대로_투수를_쓴다() {
        Map<Integer, List<Integer>> choices = RepeatService.choicesByHalf(List.of(
                decision(8, 100), decision(8, 100), decision(8, 200),
                decision(9, 200)));
        List<Integer> eighth = choices.get(8 * 2 + 1);

        assertThat(RepeatService.myPitcher(eighth, 0, 100)).isEqualTo(100);
        assertThat(RepeatService.myPitcher(eighth, 2, 100)).isEqualTo(200);
        assertThat(RepeatService.myPitcher(eighth, 7, 100)).isEqualTo(200);   // 타석이 더 길면 마지막 선택
        assertThat(RepeatService.myPitcher(choices.get(7 * 2 + 1), 0, 300)).isEqualTo(300);  // 선택 없던 반이닝
    }

    @Test
    void 분위수는_선형_보간() {
        double[] v = {0, 1, 2, 3, 4};
        assertThat(RepeatService.quantile(v, 0.5)).isEqualTo(2);
        assertThat(RepeatService.quantile(v, 0.1)).isCloseTo(0.4, within(1e-9));
        assertThat(RepeatService.quantile(new double[0], 0.5)).isZero();
    }

    private static Decision decision(int inning, int pitcherId) {
        return new Decision(inning, Half.BOT, 0, "0", pitcherId, null, false);
    }
}
