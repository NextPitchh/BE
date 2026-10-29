package com.nemosdream.be.weli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.nemosdream.be.support.PostgresTestSupport;
import com.nemosdream.be.weli.dto.WeLi;
import com.nemosdream.be.weli.service.WeLiLookupService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest
@Sql(scripts = {"file:etl/schema/04_we_li_table.sql", "classpath:sql/fixture-we-li.sql"})
@Sql(statements = "DROP TABLE IF EXISTS we_li_table", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class WeLiLookupServiceTest extends PostgresTestSupport {

    @Autowired
    WeLiLookupService service;

    @Test
    void 상황으로_WE와_LI를_찾는다() {
        WeLi v = service.lookup(9, "bot", 2, "123", -1);   // 9회말 2사 만루 1점 차 추격
        assertThat(v.we()).isCloseTo(0.461695, within(1e-6));
        assertThat(v.leverageIndex()).isCloseTo(9.793598, within(1e-5));
        assertThat(v.weFor(false)).isCloseTo(1 - 0.461695, within(1e-6));   // 수비팀 관점
    }

    @Test
    void 점수차는_4로_잘라서_찾는다() {
        assertThat(service.lookup(9, "bot", 2, "123", -7)).isEqualTo(service.lookup(9, "bot", 2, "123", -4));
        assertThat(service.lookup(5, "top", 0, "0", 10).we()).isCloseTo(0.9387, within(1e-6));
    }

    @Test
    void 표에_없는_상황은_예외() {
        assertThatThrownBy(() -> service.lookup(10, "top", 0, "0", 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.lookup(9, "mid", 0, "0", 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.lookup(9, "bot", 3, "0", 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.lookup(9, "bot", 0, "21", 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
