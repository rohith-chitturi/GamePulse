package com.gamepulse.player;

import com.gamepulse.core.exception.DuplicateResourceException;
import com.gamepulse.core.exception.ResourceNotFoundException;
import com.gamepulse.player.dto.CreatePlayerRequest;
import com.gamepulse.player.dto.PlayerResponse;
import com.gamepulse.player.model.Player;
import com.gamepulse.player.model.PlayerStatus;
import com.gamepulse.player.repository.PlayerRepository;
import com.gamepulse.player.service.PlayerService;
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
class PlayerServiceTest {

    @Mock
    private PlayerRepository playerRepository;

    private PlayerService playerService;

    @BeforeEach
    void setUp() {
        playerService = new PlayerService(playerRepository);
    }

    @Test
    @DisplayName("Create Player: Successfully creates profile with valid payload")
    void testCreatePlayerSuccess() {
        CreatePlayerRequest request = new CreatePlayerRequest("ApexStriker", "striker@ea.com", 1400);
        when(playerRepository.existsByUsernameIgnoreCase("ApexStriker")).thenReturn(false);
        when(playerRepository.existsByEmailIgnoreCase("striker@ea.com")).thenReturn(false);

        Player savedPlayer = new Player("ApexStriker", "striker@ea.com", 1400);
        savedPlayer.setId(UUID.randomUUID());
        when(playerRepository.save(any(Player.class))).thenReturn(savedPlayer);

        PlayerResponse response = playerService.createPlayer(request);

        assertThat(response).isNotNull();
        assertThat(response.getUsername()).isEqualTo("ApexStriker");
        assertThat(response.getMmr()).isEqualTo(1400);
        assertThat(response.getStatus()).isEqualTo(PlayerStatus.ONLINE);
    }

    @Test
    @DisplayName("Create Player: Fails when username is already registered")
    void testCreatePlayerDuplicateUsername() {
        CreatePlayerRequest request = new CreatePlayerRequest("ApexStriker", "striker2@ea.com", 1200);
        when(playerRepository.existsByUsernameIgnoreCase("ApexStriker")).thenReturn(true);

        assertThatThrownBy(() -> playerService.createPlayer(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("ApexStriker");
    }

    @Test
    @DisplayName("Create Player: Fails when email is already registered")
    void testCreatePlayerDuplicateEmail() {
        CreatePlayerRequest request = new CreatePlayerRequest("NewUser", "existing@ea.com", 1200);
        when(playerRepository.existsByUsernameIgnoreCase("NewUser")).thenReturn(false);
        when(playerRepository.existsByEmailIgnoreCase("existing@ea.com")).thenReturn(true);

        assertThatThrownBy(() -> playerService.createPlayer(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("existing@ea.com");
    }

    @Test
    @DisplayName("Get Player: Throws ResourceNotFoundException when player ID does not exist")
    void testGetPlayerNotFound() {
        UUID randomId = UUID.randomUUID();
        when(playerRepository.findById(randomId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> playerService.getPlayerById(randomId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Update Status: Modifies player state correctly")
    void testUpdatePlayerStatus() {
        UUID playerId = UUID.randomUUID();
        Player player = new Player("PlayerOne", "p1@ea.com", 1100);
        player.setId(playerId);
        when(playerRepository.findById(playerId)).thenReturn(Optional.of(player));

        playerService.updatePlayerStatus(playerId, PlayerStatus.IN_QUEUE);

        assertThat(player.getStatus()).isEqualTo(PlayerStatus.IN_QUEUE);
        verify(playerRepository).save(player);
    }
}
