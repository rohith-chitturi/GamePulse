package com.gamepulse.player.controller;

import com.gamepulse.player.dto.CreatePlayerRequest;
import com.gamepulse.player.dto.PlayerResponse;
import com.gamepulse.player.service.PlayerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/players")
@Tag(name = "Player Service", description = "Endpoints for player registration, profiles, and status tracking")
public class PlayerController {

    private final PlayerService playerService;

    public PlayerController(PlayerService playerService) {
        this.playerService = playerService;
    }

    @PostMapping
    @Operation(summary = "Register a new player profile", description = "Creates a player profile with validated unique username, email, and baseline MMR")
    public ResponseEntity<PlayerResponse> createPlayer(@Valid @RequestBody CreatePlayerRequest request) {
        PlayerResponse created = playerService.createPlayer(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get player profile by ID", description = "Retrieves authoritative player profile and state by UUID")
    public ResponseEntity<PlayerResponse> getPlayerById(@PathVariable UUID id) {
        return ResponseEntity.ok(playerService.getPlayerById(id));
    }

    @GetMapping
    @Operation(summary = "List player profiles with pagination", description = "Returns a paginated list of players sorted by creation time")
    public ResponseEntity<Page<PlayerResponse>> listPlayers(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(playerService.listPlayers(pageable));
    }
}
