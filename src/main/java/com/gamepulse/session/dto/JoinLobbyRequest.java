package com.gamepulse.session.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public class JoinLobbyRequest {

    @NotNull(message = "Player ID is required")
    private UUID playerId;

    private Integer team;

    public JoinLobbyRequest() {
    }

    public JoinLobbyRequest(UUID playerId, Integer team) {
        this.playerId = playerId;
        this.team = team;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public void setPlayerId(UUID playerId) {
        this.playerId = playerId;
    }

    public Integer getTeam() {
        return team;
    }

    public void setTeam(Integer team) {
        this.team = team;
    }
}
