package com.ea.gamepulse.session.dto;

import com.ea.gamepulse.session.model.MatchSession;
import com.ea.gamepulse.session.model.MatchStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class MatchSessionResponse {

    private UUID id;
    private String title;
    private String gameMode;
    private int maxPlayers;
    private int currentPlayers;
    private MatchStatus status;
    private UUID winnerId;
    private String cancellationReason;
    private Instant createdAt;
    private Instant startedAt;
    private Instant completedAt;
    private List<MatchPlayerResponse> players;

    public MatchSessionResponse() {
    }

    public MatchSessionResponse(UUID id, String title, String gameMode, int maxPlayers, int currentPlayers,
                                MatchStatus status, UUID winnerId, String cancellationReason,
                                Instant createdAt, Instant startedAt, Instant completedAt,
                                List<MatchPlayerResponse> players) {
        this.id = id;
        this.title = title;
        this.gameMode = gameMode;
        this.maxPlayers = maxPlayers;
        this.currentPlayers = currentPlayers;
        this.status = status;
        this.winnerId = winnerId;
        this.cancellationReason = cancellationReason;
        this.createdAt = createdAt;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.players = players;
    }

    public static MatchSessionResponse fromEntity(MatchSession session) {
        List<MatchPlayerResponse> playerDtos = session.getPlayers().stream()
                .map(MatchPlayerResponse::fromEntity)
                .toList();

        return new MatchSessionResponse(
                session.getId(),
                session.getTitle(),
                session.getGameMode(),
                session.getMaxPlayers(),
                playerDtos.size(),
                session.getStatus(),
                session.getWinnerId(),
                session.getCancellationReason(),
                session.getCreatedAt(),
                session.getStartedAt(),
                session.getCompletedAt(),
                playerDtos
        );
    }

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getGameMode() {
        return gameMode;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public int getCurrentPlayers() {
        return currentPlayers;
    }

    public MatchStatus getStatus() {
        return status;
    }

    public UUID getWinnerId() {
        return winnerId;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public List<MatchPlayerResponse> getPlayers() {
        return players;
    }
}
