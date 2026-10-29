package com.ea.gamepulse.matchmaking.repository;

import com.ea.gamepulse.matchmaking.model.MatchmakingTicket;
import com.ea.gamepulse.matchmaking.model.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MatchmakingTicketRepository extends JpaRepository<MatchmakingTicket, UUID> {

    boolean existsByPlayerIdAndStatus(UUID playerId, TicketStatus status);

    Optional<MatchmakingTicket> findByPlayerIdAndStatus(UUID playerId, TicketStatus status);

    List<MatchmakingTicket> findByStatusOrderByEnqueuedAtAsc(TicketStatus status);

    long countByStatus(TicketStatus status);
}
