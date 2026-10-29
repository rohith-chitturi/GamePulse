package com.gamepulse.session.repository;

import com.gamepulse.session.model.MatchSessionPlayer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface MatchSessionPlayerRepository extends JpaRepository<MatchSessionPlayer, UUID> {

    boolean existsByMatchSessionIdAndPlayerId(UUID matchSessionId, UUID playerId);

    long countByMatchSessionId(UUID matchSessionId);
}
