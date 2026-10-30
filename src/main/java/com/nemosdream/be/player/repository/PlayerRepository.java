package com.nemosdream.be.player.repository;

import com.nemosdream.be.player.entity.Player;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayerRepository extends JpaRepository<Player, Integer> {
}
