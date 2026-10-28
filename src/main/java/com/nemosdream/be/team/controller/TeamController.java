package com.nemosdream.be.team.controller;

import com.nemosdream.be.team.dto.TeamResponse;
import com.nemosdream.be.team.service.TeamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Team", description = "팀")
@RestController
public class TeamController {

    private final TeamService teamService;

    public TeamController(TeamService teamService) {
        this.teamService = teamService;
    }

    @Operation(summary = "팀 목록", description = "KBO 10개 구단. 팀 코드 순 정렬. 로고가 없으면 logo_url 은 null.")
    @GetMapping("/teams")
    public List<TeamResponse> getTeams() {
        return teamService.getTeams();
    }
}
