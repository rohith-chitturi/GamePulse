package com.ea.gamepulse.session.model;

/**
 * State machine for multiplayer match sessions and lobbies.
 */
public enum MatchStatus {
    INITIALIZING,
    WAITING_FOR_PLAYERS,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED;

    public boolean canTransitionTo(MatchStatus target) {
        if (this == target) {
            return true;
        }
        return switch (this) {
            case INITIALIZING -> target == WAITING_FOR_PLAYERS || target == IN_PROGRESS || target == CANCELLED;
            case WAITING_FOR_PLAYERS -> target == IN_PROGRESS || target == CANCELLED;
            case IN_PROGRESS -> target == COMPLETED || target == CANCELLED;
            case COMPLETED, CANCELLED -> false; // Terminal states
        };
    }
}
