package com.ea.gamepulse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.web.config.EnableSpringDataWebSupport;

/**
 * GamePulse: Chaos-Resilient Gameplay Services Platform.
 * 
 * Production-minded backend simulating online multiplayer gameplay services:
 * Player Profiles, Matchmaking Queue, Lobby/Match Lifecycle, and Observability.
 */
@SpringBootApplication
@EnableSpringDataWebSupport(pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
public class GamePulseApplication {

    public static void main(String[] args) {
        SpringApplication.run(GamePulseApplication.class, args);
    }
}
