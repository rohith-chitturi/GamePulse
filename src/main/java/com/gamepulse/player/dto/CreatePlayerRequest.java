package com.gamepulse.player.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class CreatePlayerRequest {

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 30, message = "Username must be between 3 and 30 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Username can only contain alphanumeric characters, underscores, and hyphens")
    private String username;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    @Min(value = 0, message = "Initial MMR cannot be negative")
    @Max(value = 5000, message = "Initial MMR cannot exceed 5000")
    private Integer initialMmr;

    public CreatePlayerRequest() {
    }

    public CreatePlayerRequest(String username, String email, Integer initialMmr) {
        this.username = username;
        this.email = email;
        this.initialMmr = initialMmr;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Integer getInitialMmr() {
        return initialMmr;
    }

    public void setInitialMmr(Integer initialMmr) {
        this.initialMmr = initialMmr;
    }
}
