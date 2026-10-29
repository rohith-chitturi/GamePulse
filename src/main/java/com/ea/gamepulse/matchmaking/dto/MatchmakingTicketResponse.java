package com.ea.gamepulse.matchmaking.dto;

import com.ea.gamepulse.matchmaking.model.MatchmakingTicket;
import com.ea.gamepulse.matchmaking.model.TicketStatus;

import java.time.Instant;
import java.util.UUID;

public class MatchmakingTicketResponse {

    private UUID ticketId;
    private UUID playerId;
    private int playerMmr;
    private TicketStatus status;
    private UUID matchSessionId;
    private Instant enqueuedAt;
    private Instant matchedAt;
    private Instant cancelledAt;

    public MatchmakingTicketResponse() {
    }

    public MatchmakingTicketResponse(UUID ticketId, UUID playerId, int playerMmr, TicketStatus status,
                                     UUID matchSessionId, Instant enqueuedAt, Instant matchedAt, Instant cancelledAt) {
        this.ticketId = ticketId;
        this.playerId = playerId;
        this.playerMmr = playerMmr;
        this.status = status;
        this.matchSessionId = matchSessionId;
        this.enqueuedAt = enqueuedAt;
        this.matchedAt = matchedAt;
        this.cancelledAt = cancelledAt;
    }

    public static MatchmakingTicketResponse fromEntity(MatchmakingTicket ticket) {
        return new MatchmakingTicketResponse(
                ticket.getId(),
                ticket.getPlayerId(),
                ticket.getPlayerMmr(),
                ticket.getStatus(),
                ticket.getMatchSessionId(),
                ticket.getEnqueuedAt(),
                ticket.getMatchedAt(),
                ticket.getCancelledAt()
        );
    }

    public UUID getTicketId() {
        return ticketId;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public int getPlayerMmr() {
        return playerMmr;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public UUID getMatchSessionId() {
        return matchSessionId;
    }

    public Instant getEnqueuedAt() {
        return enqueuedAt;
    }

    public Instant getMatchedAt() {
        return matchedAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }
}
