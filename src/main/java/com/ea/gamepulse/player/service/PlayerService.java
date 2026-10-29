package com.ea.gamepulse.player.service;

import com.ea.gamepulse.core.exception.DuplicateResourceException;
import com.ea.gamepulse.core.exception.ResourceNotFoundException;
import com.ea.gamepulse.player.dto.CreatePlayerRequest;
import com.ea.gamepulse.player.dto.PlayerResponse;
import com.ea.gamepulse.player.model.Player;
import com.ea.gamepulse.player.model.PlayerStatus;
import com.ea.gamepulse.player.repository.PlayerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class PlayerService {

    private static final Logger log = LoggerFactory.getLogger(PlayerService.class);
    private final PlayerRepository playerRepository;

    public PlayerService(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
    }

    @Transactional
    public PlayerResponse createPlayer(CreatePlayerRequest request) {
        if (playerRepository.existsByUsernameIgnoreCase(request.getUsername())) {
            throw new DuplicateResourceException("Player", "username", request.getUsername());
        }
        if (playerRepository.existsByEmailIgnoreCase(request.getEmail())) {
            throw new DuplicateResourceException("Player", "email", request.getEmail());
        }

        int initialMmr = (request.getInitialMmr() != null && request.getInitialMmr() > 0)
                ? request.getInitialMmr()
                : 1000;

        Player player = new Player(request.getUsername(), request.getEmail(), initialMmr);
        Player saved = playerRepository.save(player);
        log.info("Player profile created: id={}, username={}, mmr={}", saved.getId(), saved.getUsername(), saved.getMmr());
        return PlayerResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public PlayerResponse getPlayerById(UUID id) {
        Player player = getPlayerEntity(id);
        return PlayerResponse.fromEntity(player);
    }

    @Transactional(readOnly = true)
    public PlayerResponse getPlayerByUsername(String username) {
        Player player = playerRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new ResourceNotFoundException("Player", username));
        return PlayerResponse.fromEntity(player);
    }

    @Transactional(readOnly = true)
    public Page<PlayerResponse> listPlayers(Pageable pageable) {
        return playerRepository.findAll(pageable).map(PlayerResponse::fromEntity);
    }

    @Transactional
    public void updatePlayerStatus(UUID id, PlayerStatus newStatus) {
        Player player = getPlayerEntity(id);
        log.debug("Updating player status: id={}, old={}, new={}", id, player.getStatus(), newStatus);
        player.setStatus(newStatus);
        playerRepository.save(player);
    }

    @Transactional(readOnly = true)
    public Player getPlayerEntity(UUID id) {
        return playerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Player", id));
    }
}
