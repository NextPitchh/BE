package com.nemosdream.be.simulation.entity;

import com.nemosdream.be.simulation.domain.BucketCounts;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** 선수별 그 경기 전 2025 시즌 타석 결과 수. 역할(타자 B / 투수 P)마다 한 행. */
@Entity
@Table(name = "outcome_counts")
public class OutcomeCount {

    @EmbeddedId
    private OutcomeCountId id;

    private short pa;

    @Column(name = "c_1b")
    private short single;
    @Column(name = "c_2b")
    private short dbl;
    @Column(name = "c_3b")
    private short triple;
    @Column(name = "c_hr")
    private short homeRun;
    @Column(name = "c_bb")
    private short walk;
    @Column(name = "c_so")
    private short strikeout;
    @Column(name = "c_fo")
    private short flyOut;
    @Column(name = "c_gb")
    private short groundBall;

    protected OutcomeCount() {
    }

    public OutcomeCountId getId() {
        return id;
    }

    public BucketCounts toBucketCounts() {
        return new BucketCounts(pa, single, dbl, triple, homeRun, walk, strikeout, flyOut, groundBall);
    }
}
