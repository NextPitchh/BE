package com.nemosdream.be.team.dto;

import com.nemosdream.be.team.entity.Team;
/** 팀은 code 로만 식별한다 (이름은 과거 경기에도 현재 명칭이 소급됨). */
public record TeamResponse(String code, String name, String logoUrl) {

    public static TeamResponse from(Team team) {
        return new TeamResponse(team.getCode(), team.getName(), team.getLogoUrl());
    }
}
