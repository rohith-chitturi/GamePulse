package com.ea.gamepulse.matchmaking.controller;

import com.ea.gamepulse.matchmaking.dto.EnqueueRequest;
import com.ea.gamepulse.matchmaking.dto.MatchmakingTicketResponse;
import com.ea.gamepulse.matchmaking.service.MatchmakingService;
import com.ea.gamepulse.session.dto.MatchSessionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/matchmaking")
@Tag(name = "Matchmaking Service", description = "Endpoints for deterministic matchmaking queue management and ticket lifecycle")
public class MatchmakingController {

    private final MatchmakingService matchmakingService;

    public MatchmakingController(MatchmakingService matchmakingService) {
        this.matchmakingService = matchmakingService;
    }

    @PostMapping("/queue")
    @Operation(summary = "Enqueue player into matchmaking", description = "Enters player into fair FIFO matchmaking queue with expanding MMR window tolerance")
    public ResponseEntity<MatchmakingTicketResponse> enqueue(@Valid @RequestBody EnqueueRequest request) {
        MatchmakingTicketResponse response = matchmakingService.enqueuePlayer(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/tickets/{ticketId}")
    @Operation(summary = "Get matchmaking ticket status", description = "Retrieves current ticket state (QUEUED, MATCHED, CANCELLED) and matched session ID")
    public ResponseEntity<MatchmakingTicketResponse> getTicketStatus(@PathVariable UUID ticketId) {
        return ResponseEntity.ok(matchmakingService.getTicketStatus(ticketId));
    }

    @PostMapping("/tickets/{ticketId}/cancel")
    @Operation(summary = "Cancel matchmaking ticket", description = "Safely leaves matchmaking queue and restores player status to ONLINE")
    public ResponseEntity<MatchmakingTicketResponse> cancelTicket(@PathVariable UUID ticketId) {
        return ResponseEntity.ok(matchmakingService.cancelTicket(ticketId));
    }

    @PostMapping("/process")
    @Operation(summary = "Manually trigger match evaluation", description = "Evaluates queued tickets and forms matches based on deterministic MMR tolerances")
    public ResponseEntity<List<MatchSessionResponse>> processQueue() {
        return ResponseEntity.ok(matchmakingService.evaluateMatches());
    }

    @GetMapping("/queue/depth")
    @Operation(summary = "Get active queue depth", description = "Returns total count of currently waiting players in queue")
    public ResponseEntity<Map<String, Object>> getQueueDepth() {
        return ResponseEntity.ok(Map.of(
                "queueDepth", matchmakingService.getQueueDepth()
        ));
    }
}
