package com.nemosdream.be.weli.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "li_percentile")
public class LiPercentile {

    @Id
    @Column(name = "li_value")
    private BigDecimal liValue;

    private short percentile;

    protected LiPercentile() {
    }

    public BigDecimal getLiValue() {
        return liValue;
    }

    public short getPercentile() {
        return percentile;
    }
}
