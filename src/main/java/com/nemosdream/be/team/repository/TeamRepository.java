package com.nemosdream.be.team.repository;

import com.nemosdream.be.team.entity.Team;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamRepository extends JpaRepository<Team, String> {
}
