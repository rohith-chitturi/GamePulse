package com.gamepulse.session.dto;

import com.gamepulse.session.model.MatchSessionPlayer;

import java.time.Instant;
import java.util.UUID;

public class MatchPlayerResponse {

    private UUID playerId;
    private int team;
    private Instant joinedAt;

    public MatchPlayerResponse() {
    }

    public MatchPlayerResponse(UUID playerId, int team, Instant joinedAt) {
        this.playerId = playerId;
        this.team = team;
        this.joinedAt = joinedAt;
    }

    public static MatchPlayerResponse fromEntity(MatchSessionPlayer player) {
        return new MatchPlayerResponse(
                player.getPlayerId(),
                player.getTeam(),
                player.getJoinedAt()
        );
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public int getTeam() {
        return team;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }
}
