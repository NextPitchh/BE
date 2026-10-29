package com.nemosdream.be.weli.repository;

import com.nemosdream.be.weli.entity.WeLiEntry;
import com.nemosdream.be.weli.entity.WeLiKey;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WeLiRepository extends JpaRepository<WeLiEntry, WeLiKey> {
}
