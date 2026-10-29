package com.ea.gamepulse.matchmaking.service;

import com.ea.gamepulse.core.exception.DuplicateResourceException;
import com.ea.gamepulse.core.exception.InvalidStateTransitionException;
import com.ea.gamepulse.core.exception.ResourceNotFoundException;
import com.ea.gamepulse.matchmaking.dto.EnqueueRequest;
import com.ea.gamepulse.matchmaking.dto.MatchmakingTicketResponse;
import com.ea.gamepulse.matchmaking.model.MatchmakingTicket;
import com.ea.gamepulse.matchmaking.model.TicketStatus;
import com.ea.gamepulse.matchmaking.repository.MatchmakingTicketRepository;
import com.ea.gamepulse.player.model.Player;
import com.ea.gamepulse.player.model.PlayerStatus;
import com.ea.gamepulse.player.service.PlayerService;
import com.ea.gamepulse.session.dto.MatchSessionResponse;
import com.ea.gamepulse.session.service.MatchSessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class MatchmakingService {

    private static final Logger log = LoggerFactory.getLogger(MatchmakingService.class);

    private final MatchmakingTicketRepository ticketRepository;
    private final PlayerService playerService;
    private final MatchSessionService matchSessionService;

    @Value("${gamepulse.matchmaking.base-tolerance:100}")
    private int baseTolerance;

    @Value("${gamepulse.matchmaking.expansion-rate-per-sec:5}")
    private int expansionRatePerSec;

    @Value("${gamepulse.matchmaking.max-tolerance:1000}")
    private int maxTolerance;

    public MatchmakingService(MatchmakingTicketRepository ticketRepository,
                              PlayerService playerService,
                              MatchSessionService matchSessionService) {
        this.ticketRepository = ticketRepository;
        this.playerService = playerService;
        this.matchSessionService = matchSessionService;
    }

    @Transactional
    public MatchmakingTicketResponse enqueuePlayer(EnqueueRequest request) {
        Player player = playerService.getPlayerEntity(request.getPlayerId());

        if (ticketRepository.existsByPlayerIdAndStatus(player.getId(), TicketStatus.QUEUED)) {
            throw new DuplicateResourceException("MatchmakingTicket", "playerId", player.getId());
        }

        if (player.getStatus() == PlayerStatus.IN_MATCH) {
            throw new IllegalStateException(String.format("Player %s is currently in an active match", player.getId()));
        }

        MatchmakingTicket ticket = new MatchmakingTicket(player.getId(), player.getMmr());
        playerService.updatePlayerStatus(player.getId(), PlayerStatus.IN_QUEUE);
        MatchmakingTicket saved = ticketRepository.save(ticket);
        log.info("Player {} entered matchmaking queue: ticketId={}, mmr={}", player.getId(), saved.getId(), saved.getPlayerMmr());

        // Attempt deterministic match evaluation
        evaluateMatches();

        // Reload ticket state in case it matched immediately
        MatchmakingTicket current = ticketRepository.findById(saved.getId()).orElse(saved);
        return MatchmakingTicketResponse.fromEntity(current);
    }

    @Transactional
    public MatchmakingTicketResponse cancelTicket(UUID ticketId) {
        MatchmakingTicket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("MatchmakingTicket", ticketId));

        if (ticket.getStatus() != TicketStatus.QUEUED) {
            throw new InvalidStateTransitionException("MatchmakingTicket", ticket.getStatus().name(), TicketStatus.CANCELLED.name());
        }

        ticket.setStatus(TicketStatus.CANCELLED);
        ticket.setCancelledAt(Instant.now());
        playerService.updatePlayerStatus(ticket.getPlayerId(), PlayerStatus.ONLINE);

        MatchmakingTicket updated = ticketRepository.save(ticket);
        log.info("Matchmaking ticket cancelled: ticketId={}, playerId={}", ticketId, ticket.getPlayerId());
        return MatchmakingTicketResponse.fromEntity(updated);
    }

    @Transactional(readOnly = true)
    public MatchmakingTicketResponse getTicketStatus(UUID ticketId) {
        MatchmakingTicket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("MatchmakingTicket", ticketId));
        return MatchmakingTicketResponse.fromEntity(ticket);
    }

    @Transactional
    public List<MatchSessionResponse> evaluateMatches() {
        List<MatchmakingTicket> queuedTickets = ticketRepository.findByStatusOrderByEnqueuedAtAsc(TicketStatus.QUEUED);
        List<MatchSessionResponse> formedMatches = new ArrayList<>();
        Set<UUID> matchedTicketIds = new HashSet<>();
        Instant now = Instant.now();

        for (int i = 0; i < queuedTickets.size(); i++) {
            MatchmakingTicket ticketA = queuedTickets.get(i);
            if (matchedTicketIds.contains(ticketA.getId())) {
                continue;
            }

            int toleranceA = calculateTolerance(ticketA.getEnqueuedAt(), now);

            for (int j = i + 1; j < queuedTickets.size(); j++) {
                MatchmakingTicket ticketB = queuedTickets.get(j);
                if (matchedTicketIds.contains(ticketB.getId())) {
                    continue;
                }

                int toleranceB = calculateTolerance(ticketB.getEnqueuedAt(), now);
                int mmrDifference = Math.abs(ticketA.getPlayerMmr() - ticketB.getPlayerMmr());
                int allowedTolerance = Math.max(toleranceA, toleranceB);

                if (mmrDifference <= allowedTolerance) {
                    // Match confirmed!
                    matchedTicketIds.add(ticketA.getId());
                    matchedTicketIds.add(ticketB.getId());

                    MatchSessionResponse match = matchSessionService.createMatchedSession(
                            "Match: 1v1 Ranked Duel",
                            "DUEL_1V1",
                            List.of(ticketA.getPlayerId(), ticketB.getPlayerId())
                    );

                    ticketA.setStatus(TicketStatus.MATCHED);
                    ticketA.setMatchedAt(now);
                    ticketA.setMatchSessionId(match.getId());

                    ticketB.setStatus(TicketStatus.MATCHED);
                    ticketB.setMatchedAt(now);
                    ticketB.setMatchSessionId(match.getId());

                    ticketRepository.save(ticketA);
                    ticketRepository.save(ticketB);

                    formedMatches.add(match);
                    log.info("Deterministic match formed: matchId={}, playerA={}, playerB={}, mmrDiff={}",
                            match.getId(), ticketA.getPlayerId(), ticketB.getPlayerId(), mmrDifference);
                    break;
                }
            }
        }

        return formedMatches;
    }

    @Transactional(readOnly = true)
    public long getQueueDepth() {
        return ticketRepository.countByStatus(TicketStatus.QUEUED);
    }

    private int calculateTolerance(Instant enqueuedAt, Instant now) {
        long secondsInQueue = Duration.between(enqueuedAt, now).getSeconds();
        long expansion = secondsInQueue * expansionRatePerSec;
        return (int) Math.min(baseTolerance + expansion, maxTolerance);
    }
}
