package com.ea.gamepulse.session.dto;

import jakarta.validation.constraints.NotBlank;

public class CancelMatchRequest {

    @NotBlank(message = "Cancellation reason is required")
    private String reason;

    public CancelMatchRequest() {
    }

    public CancelMatchRequest(String reason) {
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
