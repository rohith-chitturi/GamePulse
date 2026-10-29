package com.ea.gamepulse.session.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "match_players",
        uniqueConstraints = @UniqueConstraint(columnNames = {"match_session_id", "player_id"})
)
public class MatchSessionPlayer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "match_session_id", nullable = false)
    private MatchSession matchSession;

    @Column(name = "player_id", nullable = false)
    private UUID playerId;

    @Column(nullable = false)
    private int team;

    @Column(nullable = false, updatable = false)
    private Instant joinedAt;

    public MatchSessionPlayer() {
    }

    public MatchSessionPlayer(MatchSession matchSession, UUID playerId, int team) {
        this.matchSession = matchSession;
        this.playerId = playerId;
        this.team = team;
    }

    @PrePersist
    public void onPrePersist() {
        if (this.joinedAt == null) {
            this.joinedAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public MatchSession getMatchSession() {
        return matchSession;
    }

    public void setMatchSession(MatchSession matchSession) {
        this.matchSession = matchSession;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public void setPlayerId(UUID playerId) {
        this.playerId = playerId;
    }

    public int getTeam() {
        return team;
    }

    public void setTeam(int team) {
        this.team = team;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }
}
