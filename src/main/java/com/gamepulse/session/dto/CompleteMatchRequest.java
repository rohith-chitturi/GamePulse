package com.gamepulse.session.dto;

import java.util.UUID;

public class CompleteMatchRequest {

    private UUID winnerId;
    private String completionNotes;

    public CompleteMatchRequest() {
    }

    public CompleteMatchRequest(UUID winnerId, String completionNotes) {
        this.winnerId = winnerId;
        this.completionNotes = completionNotes;
    }

    public UUID getWinnerId() {
        return winnerId;
    }

    public void setWinnerId(UUID winnerId) {
        this.winnerId = winnerId;
    }

    public String getCompletionNotes() {
        return completionNotes;
    }

    public void setCompletionNotes(String completionNotes) {
        this.completionNotes = completionNotes;
    }
}
