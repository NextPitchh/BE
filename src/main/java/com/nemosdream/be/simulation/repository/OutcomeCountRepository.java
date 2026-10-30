package com.nemosdream.be.simulation.repository;

import com.nemosdream.be.simulation.entity.OutcomeCount;
import com.nemosdream.be.simulation.entity.OutcomeCountId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutcomeCountRepository extends JpaRepository<OutcomeCount, OutcomeCountId> {
}
