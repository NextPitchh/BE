package com.nemosdream.be.weli.repository;

import com.nemosdream.be.weli.entity.LiPercentile;
import java.math.BigDecimal;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LiPercentileRepository extends JpaRepository<LiPercentile, BigDecimal> {
}
