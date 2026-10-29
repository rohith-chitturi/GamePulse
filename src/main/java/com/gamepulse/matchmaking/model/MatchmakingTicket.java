package com.gamepulse.matchmaking.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "matchmaking_tickets",
        indexes = {
                @Index(name = "idx_mm_player_status", columnList = "player_id, status"),
                @Index(name = "idx_mm_status_enqueued", columnList = "status, enqueued_at")
        }
)
public class MatchmakingTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "player_id", nullable = false)
    private UUID playerId;

    @Column(name = "player_mmr", nullable = false)
    private int playerMmr;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private TicketStatus status;

    @Column(name = "match_session_id")
    private UUID matchSessionId;

    @Column(name = "enqueued_at", nullable = false, updatable = false)
    private Instant enqueuedAt;

    @Column(name = "matched_at")
    private Instant matchedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    public MatchmakingTicket() {
    }

    public MatchmakingTicket(UUID playerId, int playerMmr) {
        this.playerId = playerId;
        this.playerMmr = playerMmr;
        this.status = TicketStatus.QUEUED;
    }

    @PrePersist
    public void onPrePersist() {
        if (this.enqueuedAt == null) {
            this.enqueuedAt = Instant.now();
        }
        if (this.status == null) {
            this.status = TicketStatus.QUEUED;
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public void setPlayerId(UUID playerId) {
        this.playerId = playerId;
    }

    public int getPlayerMmr() {
        return playerMmr;
    }

    public void setPlayerMmr(int playerMmr) {
        this.playerMmr = playerMmr;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public void setStatus(TicketStatus status) {
        this.status = status;
    }

    public UUID getMatchSessionId() {
        return matchSessionId;
    }

    public void setMatchSessionId(UUID matchSessionId) {
        this.matchSessionId = matchSessionId;
    }

    public Instant getEnqueuedAt() {
        return enqueuedAt;
    }

    public Instant getMatchedAt() {
        return matchedAt;
    }

    public void setMatchedAt(Instant matchedAt) {
        this.matchedAt = matchedAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(Instant cancelledAt) {
        this.cancelledAt = cancelledAt;
    }
}
