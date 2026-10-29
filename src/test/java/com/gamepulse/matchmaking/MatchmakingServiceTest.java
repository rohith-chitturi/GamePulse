package com.gamepulse.matchmaking;

import com.gamepulse.core.exception.DuplicateResourceException;
import com.gamepulse.core.exception.InvalidStateTransitionException;
import com.gamepulse.matchmaking.dto.EnqueueRequest;
import com.gamepulse.matchmaking.dto.MatchmakingTicketResponse;
import com.gamepulse.matchmaking.model.MatchmakingTicket;
import com.gamepulse.matchmaking.model.TicketStatus;
import com.gamepulse.matchmaking.repository.MatchmakingTicketRepository;
import com.gamepulse.matchmaking.service.MatchmakingService;
import com.gamepulse.player.model.Player;
import com.gamepulse.player.model.PlayerStatus;
import com.gamepulse.player.service.PlayerService;
import com.gamepulse.session.dto.MatchSessionResponse;
import com.gamepulse.session.model.MatchStatus;
import com.gamepulse.session.service.MatchSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchmakingServiceTest {

    @Mock
    private MatchmakingTicketRepository ticketRepository;

    @Mock
    private PlayerService playerService;

    @Mock
    private MatchSessionService matchSessionService;

    private MatchmakingService matchmakingService;

    @BeforeEach
    void setUp() {
        matchmakingService = new MatchmakingService(ticketRepository, playerService, matchSessionService);
        ReflectionTestUtils.setField(matchmakingService, "baseTolerance", 100);
        ReflectionTestUtils.setField(matchmakingService, "expansionRatePerSec", 5);
        ReflectionTestUtils.setField(matchmakingService, "maxTolerance", 1000);
    }

    @Test
    @DisplayName("Enqueue: Successfully creates ticket and updates player status to IN_QUEUE")
    void testEnqueuePlayerSuccess() {
        UUID playerId = UUID.randomUUID();
        Player player = new Player("PilotAlpha", "pilot@ea.com", 1250);
        player.setId(playerId);
        player.setStatus(PlayerStatus.ONLINE);

        when(playerService.getPlayerEntityWithLock(playerId)).thenReturn(player);
        when(ticketRepository.existsByPlayerIdAndStatus(playerId, TicketStatus.QUEUED)).thenReturn(false);

        MatchmakingTicket ticket = new MatchmakingTicket(playerId, 1250);
        ticket.setId(UUID.randomUUID());
        when(ticketRepository.save(any(MatchmakingTicket.class))).thenReturn(ticket);
        when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));

        MatchmakingTicketResponse response = matchmakingService.enqueuePlayer(new EnqueueRequest(playerId));

        assertThat(response).isNotNull();
        assertThat(response.getPlayerId()).isEqualTo(playerId);
        assertThat(response.getPlayerMmr()).isEqualTo(1250);
        assertThat(response.getStatus()).isEqualTo(TicketStatus.QUEUED);
        verify(playerService).updatePlayerStatus(playerId, PlayerStatus.IN_QUEUE);
    }

    @Test
    @DisplayName("Enqueue: Rejects duplicate enqueue when player already has QUEUED ticket")
    void testEnqueueDuplicatePlayer() {
        UUID playerId = UUID.randomUUID();
        Player player = new Player("PilotAlpha", "pilot@ea.com", 1250);
        player.setId(playerId);

        when(playerService.getPlayerEntityWithLock(playerId)).thenReturn(player);
        when(ticketRepository.existsByPlayerIdAndStatus(playerId, TicketStatus.QUEUED)).thenReturn(true);

        assertThatThrownBy(() -> matchmakingService.enqueuePlayer(new EnqueueRequest(playerId)))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    @DisplayName("Cancel Ticket: Successfully cancels active ticket and restores player to ONLINE")
    void testCancelTicketSuccess() {
        UUID ticketId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();

        MatchmakingTicket ticket = new MatchmakingTicket(playerId, 1300);
        ticket.setId(ticketId);
        ticket.setStatus(TicketStatus.QUEUED);

        when(ticketRepository.findByIdsWithLock(List.of(ticketId))).thenReturn(List.of(ticket));
        when(ticketRepository.save(any(MatchmakingTicket.class))).thenReturn(ticket);

        MatchmakingTicketResponse response = matchmakingService.cancelTicket(ticketId);

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.CANCELLED);
        assertThat(ticket.getCancelledAt()).isNotNull();
        verify(playerService).updatePlayerStatus(playerId, PlayerStatus.ONLINE);
    }

    @Test
    @DisplayName("Cancel Ticket: Rejects cancellation if ticket is already matched")
    void testCancelTicketAlreadyMatched() {
        UUID ticketId = UUID.randomUUID();
        MatchmakingTicket ticket = new MatchmakingTicket(UUID.randomUUID(), 1300);
        ticket.setId(ticketId);
        ticket.setStatus(TicketStatus.MATCHED);

        when(ticketRepository.findByIdsWithLock(List.of(ticketId))).thenReturn(List.of(ticket));

        assertThatThrownBy(() -> matchmakingService.cancelTicket(ticketId))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    @DisplayName("Evaluate Matches: Pairs two compatible players when MMR diff is within tolerance")
    void testEvaluateMatchesCompatiblePair() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();

        MatchmakingTicket t1 = new MatchmakingTicket(p1, 1200);
        t1.setId(UUID.randomUUID());
        ReflectionTestUtils.setField(t1, "enqueuedAt", Instant.now());

        MatchmakingTicket t2 = new MatchmakingTicket(p2, 1260); // Diff = 60 <= baseTolerance 100
        t2.setId(UUID.randomUUID());
        ReflectionTestUtils.setField(t2, "enqueuedAt", Instant.now());

        when(ticketRepository.findByStatusOrderByEnqueuedAtAsc(TicketStatus.QUEUED)).thenReturn(List.of(t1, t2));
        when(ticketRepository.findByIdsWithLock(List.of(t1.getId(), t2.getId()))).thenReturn(List.of(t1, t2));

        MatchSessionResponse mockSession = new MatchSessionResponse(
                UUID.randomUUID(), "Ranked", "DUEL_1V1", 2, 2,
                MatchStatus.IN_PROGRESS, null, null,
                Instant.now(), Instant.now(), null, Collections.emptyList()
        );

        when(matchSessionService.createMatchedSession(anyString(), anyString(), anyList())).thenReturn(mockSession);

        List<MatchSessionResponse> formed = matchmakingService.evaluateMatches();

        assertThat(formed).hasSize(1);
        assertThat(t1.getStatus()).isEqualTo(TicketStatus.MATCHED);
        assertThat(t2.getStatus()).isEqualTo(TicketStatus.MATCHED);
        assertThat(t1.getMatchSessionId()).isEqualTo(mockSession.getId());
        assertThat(t2.getMatchSessionId()).isEqualTo(mockSession.getId());
    }

    @Test
    @DisplayName("Evaluate Matches: Does not pair players when MMR difference exceeds tolerance window")
    void testEvaluateMatchesIncompatibleMmr() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();

        MatchmakingTicket t1 = new MatchmakingTicket(p1, 1000);
        t1.setId(UUID.randomUUID());
        ReflectionTestUtils.setField(t1, "enqueuedAt", Instant.now());

        MatchmakingTicket t2 = new MatchmakingTicket(p2, 1800); // Diff = 800 > baseTolerance 100
        t2.setId(UUID.randomUUID());
        ReflectionTestUtils.setField(t2, "enqueuedAt", Instant.now());

        when(ticketRepository.findByStatusOrderByEnqueuedAtAsc(TicketStatus.QUEUED)).thenReturn(List.of(t1, t2));

        List<MatchSessionResponse> formed = matchmakingService.evaluateMatches();

        assertThat(formed).isEmpty();
        assertThat(t1.getStatus()).isEqualTo(TicketStatus.QUEUED);
        assertThat(t2.getStatus()).isEqualTo(TicketStatus.QUEUED);
    }
}
