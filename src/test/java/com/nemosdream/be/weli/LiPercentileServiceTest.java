package com.nemosdream.be.weli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nemosdream.be.support.PostgresTestSupport;
import com.nemosdream.be.weli.service.LiPercentileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest
@Sql(scripts = {"file:etl/schema/05_li_percentile.sql", "classpath:sql/fixture-li-percentile.sql"})
@Sql(statements = "DROP TABLE IF EXISTS li_percentile", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class LiPercentileServiceTest extends PostgresTestSupport {

    @Autowired
    LiPercentileService service;

    @Test
    void LI를_소수_둘째_자리로_반올림해_찾는다() {
        assertThat(service.percentileOf(2.41)).isEqualTo(24);
        assertThat(service.percentileOf(2.414)).isEqualTo(24);    // 2.41
        assertThat(service.percentileOf(0.996)).isEqualTo(10);    // 1.00
    }

    @Test
    void 범위_밖_LI는_끝값으로_찾는다() {
        assertThat(service.percentileOf(-0.5)).isZero();
        assertThat(service.percentileOf(10.0)).isEqualTo(100);
        assertThat(service.percentileOf(37.2)).isEqualTo(100);
    }

    @Test
    void NaN은_예외() {
        assertThatThrownBy(() -> service.percentileOf(Double.NaN)).isInstanceOf(IllegalArgumentException.class);
    }
}
