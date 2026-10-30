package com.nemosdream.be.session.domain;

import com.nemosdream.be.game.entity.GameBattingOrder;

/** 타순 자리(1~9)를 거쳐 간 타자 한 명 (game_batting_order). seq 0 = 선발 */
public record LineupEntry(String teamCode, int orderNo, int seq, int batterId, int firstPaIndex) {

    public static LineupEntry of(GameBattingOrder o) {
        return new LineupEntry(o.getTeamCode(), o.getOrderNo(), o.getSeq(), o.getBatterId(), o.getFirstPaIndex());
    }
}
