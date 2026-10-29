package com.gamepulse.session.service;

import com.gamepulse.core.exception.CapacityExceededException;
import com.gamepulse.core.exception.DuplicateResourceException;
import com.gamepulse.core.exception.InvalidStateTransitionException;
import com.gamepulse.core.exception.ResourceNotFoundException;
import com.gamepulse.player.model.Player;
import com.gamepulse.player.model.PlayerStatus;
import com.gamepulse.player.service.PlayerService;
import com.gamepulse.session.dto.CancelMatchRequest;
import com.gamepulse.session.dto.CompleteMatchRequest;
import com.gamepulse.session.dto.CreateLobbyRequest;
import com.gamepulse.session.dto.JoinLobbyRequest;
import com.gamepulse.session.dto.MatchSessionResponse;
import com.gamepulse.session.model.MatchSession;
import com.gamepulse.session.model.MatchSessionPlayer;
import com.gamepulse.session.model.MatchStatus;
import com.gamepulse.session.repository.MatchSessionPlayerRepository;
import com.gamepulse.session.repository.MatchSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class MatchSessionService {

    private static final Logger log = LoggerFactory.getLogger(MatchSessionService.class);

    private final MatchSessionRepository matchSessionRepository;
    private final MatchSessionPlayerRepository matchSessionPlayerRepository;
    private final PlayerService playerService;

    public MatchSessionService(MatchSessionRepository matchSessionRepository,
                               MatchSessionPlayerRepository matchSessionPlayerRepository,
                               PlayerService playerService) {
        this.matchSessionRepository = matchSessionRepository;
        this.matchSessionPlayerRepository = matchSessionPlayerRepository;
        this.playerService = playerService;
    }

    @Transactional
    public MatchSessionResponse createLobby(CreateLobbyRequest request) {
        MatchSession session = new MatchSession(
                request.getTitle(),
                request.getGameMode(),
                request.getMaxPlayers()
        );
        MatchSession saved = matchSessionRepository.save(session);
        log.info("Lobby created: id={}, title={}, maxPlayers={}", saved.getId(), saved.getTitle(), saved.getMaxPlayers());
        return MatchSessionResponse.fromEntity(saved);
    }

    @Transactional
    public MatchSessionResponse joinLobby(UUID sessionId, JoinLobbyRequest request) {
        MatchSession session = getSessionEntity(sessionId);

        if (session.getStatus() != MatchStatus.WAITING_FOR_PLAYERS && session.getStatus() != MatchStatus.INITIALIZING) {
            throw new InvalidStateTransitionException("MatchSession", session.getStatus().name(), "JOINING");
        }

        if (session.getPlayers().size() >= session.getMaxPlayers()) {
            throw new CapacityExceededException("MatchSession", session.getPlayers().size(), session.getMaxPlayers());
        }

        if (matchSessionPlayerRepository.existsByMatchSessionIdAndPlayerId(sessionId, request.getPlayerId())) {
            throw new DuplicateResourceException("LobbyPlayer", "playerId", request.getPlayerId());
        }

        Player player = playerService.getPlayerEntity(request.getPlayerId());
        int assignedTeam = (request.getTeam() != null && request.getTeam() > 0)
                ? request.getTeam()
                : (session.getPlayers().size() % 2) + 1;

        session.addPlayer(player.getId(), assignedTeam);
        playerService.updatePlayerStatus(player.getId(), PlayerStatus.IN_LOBBY);

        MatchSession updated = matchSessionRepository.save(session);
        log.info("Player {} joined lobby {}. Current count={}/{}", player.getId(), sessionId, updated.getPlayers().size(), updated.getMaxPlayers());
        return MatchSessionResponse.fromEntity(updated);
    }

    @Transactional
    public MatchSessionResponse createMatchedSession(String title, String gameMode, List<UUID> playerIds) {
        MatchSession session = new MatchSession(title, gameMode, playerIds.size());
        session.setStatus(MatchStatus.IN_PROGRESS);
        session.setStartedAt(Instant.now());

        for (int i = 0; i < playerIds.size(); i++) {
            UUID playerId = playerIds.get(i);
            int team = (i % 2) + 1;
            session.addPlayer(playerId, team);
            playerService.updatePlayerStatus(playerId, PlayerStatus.IN_MATCH);
        }

        MatchSession saved = matchSessionRepository.save(session);
        log.info("Direct match session formed by matchmaker: id={}, players={}", saved.getId(), playerIds.size());
        return MatchSessionResponse.fromEntity(saved);
    }

    @Transactional
    public MatchSessionResponse startMatch(UUID sessionId) {
        MatchSession session = getSessionEntity(sessionId);

        if (!session.getStatus().canTransitionTo(MatchStatus.IN_PROGRESS)) {
            throw new InvalidStateTransitionException("MatchSession", session.getStatus().name(), MatchStatus.IN_PROGRESS.name());
        }

        if (session.getPlayers().size() < 2) {
            throw new IllegalStateException("Cannot start match with fewer than 2 players in lobby");
        }

        session.setStatus(MatchStatus.IN_PROGRESS);
        session.setStartedAt(Instant.now());

        for (MatchSessionPlayer p : session.getPlayers()) {
            playerService.updatePlayerStatus(p.getPlayerId(), PlayerStatus.IN_MATCH);
        }

        MatchSession updated = matchSessionRepository.save(session);
        log.info("Match session started: id={}", sessionId);
        return MatchSessionResponse.fromEntity(updated);
    }

    @Transactional
    public MatchSessionResponse completeMatch(UUID sessionId, CompleteMatchRequest request) {
        MatchSession session = getSessionEntity(sessionId);

        if (!session.getStatus().canTransitionTo(MatchStatus.COMPLETED)) {
            throw new InvalidStateTransitionException("MatchSession", session.getStatus().name(), MatchStatus.COMPLETED.name());
        }

        session.setStatus(MatchStatus.COMPLETED);
        session.setCompletedAt(Instant.now());
        if (request != null && request.getWinnerId() != null) {
            session.setWinnerId(request.getWinnerId());
        }

        for (MatchSessionPlayer p : session.getPlayers()) {
            playerService.updatePlayerStatus(p.getPlayerId(), PlayerStatus.ONLINE);
        }

        MatchSession updated = matchSessionRepository.save(session);
        log.info("Match session completed: id={}, winnerId={}", sessionId, session.getWinnerId());
        return MatchSessionResponse.fromEntity(updated);
    }

    @Transactional
    public MatchSessionResponse cancelMatch(UUID sessionId, CancelMatchRequest request) {
        MatchSession session = getSessionEntity(sessionId);

        if (!session.getStatus().canTransitionTo(MatchStatus.CANCELLED)) {
            throw new InvalidStateTransitionException("MatchSession", session.getStatus().name(), MatchStatus.CANCELLED.name());
        }

        session.setStatus(MatchStatus.CANCELLED);
        session.setCompletedAt(Instant.now());
        session.setCancellationReason(request != null ? request.getReason() : "Host cancelled");

        for (MatchSessionPlayer p : session.getPlayers()) {
            playerService.updatePlayerStatus(p.getPlayerId(), PlayerStatus.ONLINE);
        }

        MatchSession updated = matchSessionRepository.save(session);
        log.info("Match session cancelled: id={}, reason={}", sessionId, session.getCancellationReason());
        return MatchSessionResponse.fromEntity(updated);
    }

    @Transactional(readOnly = true)
    public MatchSessionResponse getMatchSession(UUID sessionId) {
        return MatchSessionResponse.fromEntity(getSessionEntity(sessionId));
    }

    @Transactional(readOnly = true)
    public Page<MatchSessionResponse> listMatchSessions(MatchStatus status, Pageable pageable) {
        Page<MatchSession> page = (status != null)
                ? matchSessionRepository.findByStatus(status, pageable)
                : matchSessionRepository.findAll(pageable);
        return page.map(MatchSessionResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public MatchSession getSessionEntity(UUID id) {
        return matchSessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MatchSession", id));
    }
}
