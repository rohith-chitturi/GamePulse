package com.gamepulse.player.dto;

import com.gamepulse.player.model.Player;
import com.gamepulse.player.model.PlayerStatus;

import java.time.Instant;
import java.util.UUID;

public class PlayerResponse {

    private UUID id;
    private String username;
    private String email;
    private int mmr;
    private PlayerStatus status;
    private Instant createdAt;
    private Instant updatedAt;

    public PlayerResponse() {
    }

    public PlayerResponse(UUID id, String username, String email, int mmr, PlayerStatus status, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.mmr = mmr;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static PlayerResponse fromEntity(Player player) {
        return new PlayerResponse(
                player.getId(),
                player.getUsername(),
                player.getEmail(),
                player.getMmr(),
                player.getStatus(),
                player.getCreatedAt(),
                player.getUpdatedAt()
        );
    }

    public UUID getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public int getMmr() {
        return mmr;
    }

    public PlayerStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
