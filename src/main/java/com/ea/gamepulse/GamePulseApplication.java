package com.ea.gamepulse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * GamePulse: Chaos-Resilient Gameplay Services Platform.
 * 
 * Production-minded backend simulating online multiplayer gameplay services:
 * Player Profiles, Matchmaking Queue, Lobby/Match Lifecycle, and Observability.
 */
@SpringBootApplication
public class GamePulseApplication {

    public static void main(String[] args) {
        SpringApplication.run(GamePulseApplication.class, args);
    }
}
