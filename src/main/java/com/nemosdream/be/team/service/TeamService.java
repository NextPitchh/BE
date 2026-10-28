package com.nemosdream.be.team.service;

import com.nemosdream.be.global.error.BusinessException;
import com.nemosdream.be.global.error.ErrorCode;
import com.nemosdream.be.team.dto.TeamResponse;
import com.nemosdream.be.team.repository.TeamRepository;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TeamService {

    private final TeamRepository teamRepository;

    public TeamService(TeamRepository teamRepository) {
        this.teamRepository = teamRepository;
    }

    public List<TeamResponse> getTeams() {
        return teamRepository.findAll(Sort.by("code")).stream()
                .map(TeamResponse::from)
                .toList();
    }

    public void validateExists(String code) {
        if (!teamRepository.existsById(code)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "알 수 없는 팀 코드입니다: " + code);
        }
    }
}
