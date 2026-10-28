package com.nemosdream.be.weli.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;

/** we_li_table 의 키 = 경기 상황. score_diff 는 공격팀 관점, -4~4 로 잘린 값. */
@Embeddable
public record WeLiKey(
        short inning,
        String half,
        @Column(name = "out_count") short outCount,
        @Column(name = "base_state") String baseState,
        @Column(name = "score_diff") short scoreDiff
) implements Serializable {
}
