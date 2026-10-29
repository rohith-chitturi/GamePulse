package com.gamepulse.session.controller;

import com.gamepulse.session.dto.CancelMatchRequest;
import com.gamepulse.session.dto.CompleteMatchRequest;
import com.gamepulse.session.dto.CreateLobbyRequest;
import com.gamepulse.session.dto.JoinLobbyRequest;
import com.gamepulse.session.dto.MatchSessionResponse;
import com.gamepulse.session.model.MatchStatus;
import com.gamepulse.session.service.MatchSessionService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/matches")
@Tag(name = "Lobby & Match Session Service", description = "Endpoints for multiplayer lobby creation, player joining, and match lifecycle state transitions")
public class MatchSessionController {

    private final MatchSessionService matchSessionService;

    public MatchSessionController(MatchSessionService matchSessionService) {
        this.matchSessionService = matchSessionService;
    }

    @PostMapping("/lobbies")
    @Operation(summary = "Create a new multiplayer lobby", description = "Creates a lobby in WAITING_FOR_PLAYERS state with specified game mode and player capacity")
    public ResponseEntity<MatchSessionResponse> createLobby(@Valid @RequestBody CreateLobbyRequest request) {
        MatchSessionResponse response = matchSessionService.createLobby(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/join")
    @Operation(summary = "Join an existing lobby", description = "Adds a player to an open lobby, validating capacity and preventing duplicate membership")
    public ResponseEntity<MatchSessionResponse> joinLobby(@PathVariable UUID id, @Valid @RequestBody JoinLobbyRequest request) {
        MatchSessionResponse response = matchSessionService.joinLobby(id, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/start")
    @Operation(summary = "Start match session", description = "Transitions match from WAITING_FOR_PLAYERS to IN_PROGRESS and updates player statuses")
    public ResponseEntity<MatchSessionResponse> startMatch(@PathVariable UUID id) {
        MatchSessionResponse response = matchSessionService.startMatch(id);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/complete")
    @Operation(summary = "Complete match session", description = "Records match conclusion, sets winner, and returns participating players to ONLINE status")
    public ResponseEntity<MatchSessionResponse> completeMatch(@PathVariable UUID id, @RequestBody(required = false) CompleteMatchRequest request) {
        MatchSessionResponse response = matchSessionService.completeMatch(id, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel match session", description = "Cancels match session prematurely and safely cleans up player states")
    public ResponseEntity<MatchSessionResponse> cancelMatch(@PathVariable UUID id, @Valid @RequestBody CancelMatchRequest request) {
        MatchSessionResponse response = matchSessionService.cancelMatch(id, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get match session details", description = "Retrieves authoritative match state, roster, and lifecycle timestamps")
    public ResponseEntity<MatchSessionResponse> getMatch(@PathVariable UUID id) {
        return ResponseEntity.ok(matchSessionService.getMatchSession(id));
    }

    @GetMapping
    @Operation(summary = "List match sessions with optional status filter", description = "Returns a paginated list of match sessions")
    public ResponseEntity<Page<MatchSessionResponse>> listMatches(
            @RequestParam(required = false) MatchStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(matchSessionService.listMatchSessions(status, pageable));
    }
}
