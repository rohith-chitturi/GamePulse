package com.gamepulse.session;

import com.gamepulse.core.exception.CapacityExceededException;
import com.gamepulse.core.exception.DuplicateResourceException;
import com.gamepulse.core.exception.InvalidStateTransitionException;
import com.gamepulse.player.model.Player;
import com.gamepulse.player.model.PlayerStatus;
import com.gamepulse.player.service.PlayerService;
import com.gamepulse.session.dto.CancelMatchRequest;
import com.gamepulse.session.dto.CompleteMatchRequest;
import com.gamepulse.session.dto.CreateLobbyRequest;
import com.gamepulse.session.dto.JoinLobbyRequest;
import com.gamepulse.session.dto.MatchSessionResponse;
import com.gamepulse.session.model.MatchSession;
import com.gamepulse.session.model.MatchStatus;
import com.gamepulse.session.repository.MatchSessionPlayerRepository;
import com.gamepulse.session.repository.MatchSessionRepository;
import com.gamepulse.session.service.MatchSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchSessionServiceTest {

    @Mock
    private MatchSessionRepository matchSessionRepository;

    @Mock
    private MatchSessionPlayerRepository matchSessionPlayerRepository;

    @Mock
    private PlayerService playerService;

    private MatchSessionService matchSessionService;

    @BeforeEach
    void setUp() {
        matchSessionService = new MatchSessionService(matchSessionRepository, matchSessionPlayerRepository, playerService);
    }

    @Test
    @DisplayName("Create Lobby: Successfully initializes lobby in WAITING_FOR_PLAYERS state")
    void testCreateLobby() {
        CreateLobbyRequest request = new CreateLobbyRequest("Pro Customs", "DUEL_1V1", 2);
        MatchSession session = new MatchSession("Pro Customs", "DUEL_1V1", 2);
        session.setId(UUID.randomUUID());
        when(matchSessionRepository.save(any(MatchSession.class))).thenReturn(session);

        MatchSessionResponse response = matchSessionService.createLobby(request);

        assertThat(response).isNotNull();
        assertThat(response.getTitle()).isEqualTo("Pro Customs");
        assertThat(response.getMaxPlayers()).isEqualTo(2);
        assertThat(response.getStatus()).isEqualTo(MatchStatus.WAITING_FOR_PLAYERS);
    }

    @Test
    @DisplayName("Join Lobby: Successfully adds eligible player")
    void testJoinLobbySuccess() {
        UUID sessionId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();

        MatchSession session = new MatchSession("Custom Match", "DUEL_1V1", 2);
        session.setId(sessionId);

        Player player = new Player("PlayerOne", "p1@ea.com", 1200);
        player.setId(playerId);

        when(matchSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(matchSessionPlayerRepository.existsByMatchSessionIdAndPlayerId(sessionId, playerId)).thenReturn(false);
        when(playerService.getPlayerEntity(playerId)).thenReturn(player);
        when(matchSessionRepository.save(any(MatchSession.class))).thenReturn(session);

        MatchSessionResponse response = matchSessionService.joinLobby(sessionId, new JoinLobbyRequest(playerId, 1));

        assertThat(response).isNotNull();
        verify(playerService).updatePlayerStatus(playerId, PlayerStatus.IN_LOBBY);
    }

    @Test
    @DisplayName("Join Lobby: Fails when lobby is already at maximum capacity")
    void testJoinLobbyCapacityExceeded() {
        UUID sessionId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();

        MatchSession session = new MatchSession("Custom Match", "DUEL_1V1", 2);
        session.setId(sessionId);
        session.addPlayer(UUID.randomUUID(), 1);
        session.addPlayer(UUID.randomUUID(), 2);

        when(matchSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> matchSessionService.joinLobby(sessionId, new JoinLobbyRequest(playerId, 1)))
                .isInstanceOf(CapacityExceededException.class);
    }

    @Test
    @DisplayName("Join Lobby: Fails when player is already member of lobby")
    void testJoinLobbyDuplicatePlayer() {
        UUID sessionId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();

        MatchSession session = new MatchSession("Custom Match", "DUEL_1V1", 2);
        session.setId(sessionId);

        when(matchSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(matchSessionPlayerRepository.existsByMatchSessionIdAndPlayerId(sessionId, playerId)).thenReturn(true);

        assertThatThrownBy(() -> matchSessionService.joinLobby(sessionId, new JoinLobbyRequest(playerId, 1)))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    @DisplayName("Start Match: Successfully transitions to IN_PROGRESS when requirements met")
    void testStartMatchSuccess() {
        UUID sessionId = UUID.randomUUID();
        MatchSession session = new MatchSession("Custom Match", "DUEL_1V1", 2);
        session.setId(sessionId);
        session.addPlayer(UUID.randomUUID(), 1);
        session.addPlayer(UUID.randomUUID(), 2);

        when(matchSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(matchSessionRepository.save(any(MatchSession.class))).thenReturn(session);

        MatchSessionResponse response = matchSessionService.startMatch(sessionId);

        assertThat(session.getStatus()).isEqualTo(MatchStatus.IN_PROGRESS);
        assertThat(session.getStartedAt()).isNotNull();
    }

    @Test
    @DisplayName("Complete Match: Records winner and returns players to ONLINE status")
    void testCompleteMatch() {
        UUID sessionId = UUID.randomUUID();
        UUID p1Id = UUID.randomUUID();
        UUID p2Id = UUID.randomUUID();

        MatchSession session = new MatchSession("Custom Match", "DUEL_1V1", 2);
        session.setId(sessionId);
        session.setStatus(MatchStatus.IN_PROGRESS);
        session.addPlayer(p1Id, 1);
        session.addPlayer(p2Id, 2);

        when(matchSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(matchSessionRepository.save(any(MatchSession.class))).thenReturn(session);

        MatchSessionResponse response = matchSessionService.completeMatch(sessionId, new CompleteMatchRequest(p1Id, "Victory"));

        assertThat(session.getStatus()).isEqualTo(MatchStatus.COMPLETED);
        assertThat(session.getWinnerId()).isEqualTo(p1Id);
        verify(playerService).updatePlayerStatus(p1Id, PlayerStatus.ONLINE);
        verify(playerService).updatePlayerStatus(p2Id, PlayerStatus.ONLINE);
    }

    @Test
    @DisplayName("Cancel Match: Rejects invalid transition if match is already completed")
    void testCancelMatchInvalidTransition() {
        UUID sessionId = UUID.randomUUID();
        MatchSession session = new MatchSession("Custom Match", "DUEL_1V1", 2);
        session.setId(sessionId);
        session.setStatus(MatchStatus.COMPLETED);

        when(matchSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> matchSessionService.cancelMatch(sessionId, new CancelMatchRequest("Test")))
                .isInstanceOf(InvalidStateTransitionException.class);
    }
}
