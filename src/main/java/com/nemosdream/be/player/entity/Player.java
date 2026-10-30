package com.nemosdream.be.player.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "players")
public class Player {

    @Id
    @Column(name = "player_id")
    private int id;

    private String name;

    /** 'L' | 'R' | 'U'(언더핸드). 투수가 아니면 NULL 일 수 있다 */
    @Column(name = "throws")
    private String throwingHand;

    @Column(name = "hit_type")
    private String hitType;

    @Column(name = "photo_url")
    private String photoUrl;

    protected Player() {
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getThrows() {
        return throwingHand;
    }

    public String getHitType() {
        return hitType;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }
}
