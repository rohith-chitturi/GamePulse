package com.ea.gamepulse.session.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CreateLobbyRequest {

    @NotBlank(message = "Title is required")
    @Size(min = 3, max = 64, message = "Title must be between 3 and 64 characters")
    private String title;

    @NotBlank(message = "Game mode is required")
    @Size(min = 2, max = 32, message = "Game mode must be between 2 and 32 characters")
    private String gameMode;

    @Min(value = 2, message = "Minimum lobby capacity is 2 players")
    @Max(value = 16, message = "Maximum lobby capacity is 16 players")
    private int maxPlayers = 2;

    public CreateLobbyRequest() {
    }

    public CreateLobbyRequest(String title, String gameMode, int maxPlayers) {
        this.title = title;
        this.gameMode = gameMode;
        this.maxPlayers = maxPlayers;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getGameMode() {
        return gameMode;
    }

    public void setGameMode(String gameMode) {
        this.gameMode = gameMode;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public void setMaxPlayers(int maxPlayers) {
        this.maxPlayers = maxPlayers;
    }
}
