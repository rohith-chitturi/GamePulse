package com.gamepulse.session.repository;

import com.gamepulse.session.model.MatchSession;
import com.gamepulse.session.model.MatchStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface MatchSessionRepository extends JpaRepository<MatchSession, UUID> {

    Page<MatchSession> findByStatus(MatchStatus status, Pageable pageable);
}
