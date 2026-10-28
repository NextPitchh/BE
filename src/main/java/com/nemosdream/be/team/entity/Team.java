package com.nemosdream.be.team.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** teams 테이블 */
@Entity
@Table(name = "teams")
public class Team {

    @Id
    @Column(name = "team_code")
    private String code;

    private String name;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "logo_url")
    private String logoUrl;

    protected Team() {
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getFullName() {
        return fullName;
    }

    public String getLogoUrl() {
        return logoUrl;
    }
}
