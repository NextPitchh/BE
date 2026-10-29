package com.nemosdream.be.weli.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "we_li_table")
public class WeLiEntry {

    @EmbeddedId
    private WeLiKey key;

    private float we;

    @Column(name = "leverage_index")
    private float leverageIndex;

    protected WeLiEntry() {
    }

    public WeLiKey getKey() {
        return key;
    }

    public float getWe() {
        return we;
    }

    public float getLeverageIndex() {
        return leverageIndex;
    }
}
