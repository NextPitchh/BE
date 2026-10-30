package com.nemosdream.be.game.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** 타순 자리(1~9)를 거쳐 간 타자. 시점 t 의 라인업 = 자리마다 first_pa_index <= t 인 마지막(seq 가 가장 큰) 타자. */
@Entity
@Table(name = "game_batting_order")
public class GameBattingOrder {

    @EmbeddedId
    private GameBattingOrderId id;

    @Column(name = "batter_id")
    private int batterId;

    /** 예고 라인업 포지션 (한글, 예: "좌익수"). 교체 선수는 NULL */
    private String position;

    @Column(name = "first_pa_index")
    private short firstPaIndex;

    protected GameBattingOrder() {
    }

    public String getTeamCode() {
        return id.teamCode();
    }

    public int getOrderNo() {
        return id.orderNo();
    }

    public int getSeq() {
        return id.seq();
    }

    public int getBatterId() {
        return batterId;
    }

    public String getPosition() {
        return position;
    }

    public short getFirstPaIndex() {
        return firstPaIndex;
    }
}
