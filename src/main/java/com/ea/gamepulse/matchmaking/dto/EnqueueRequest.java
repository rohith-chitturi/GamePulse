package com.ea.gamepulse.matchmaking.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public class EnqueueRequest {

    @NotNull(message = "Player ID is required to enter matchmaking queue")
    private UUID playerId;

    public EnqueueRequest() {
    }

    public EnqueueRequest(UUID playerId) {
        this.playerId = playerId;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public void setPlayerId(UUID playerId) {
        this.playerId = playerId;
    }
}
