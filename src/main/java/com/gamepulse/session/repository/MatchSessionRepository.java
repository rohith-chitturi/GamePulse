package com.gamepulse.session.repository;

import com.gamepulse.session.model.MatchSession;
import com.gamepulse.session.model.MatchStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MatchSessionRepository extends JpaRepository<MatchSession, UUID> {

    Page<MatchSession> findByStatus(MatchStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM MatchSession s WHERE s.id = :id")
    Optional<MatchSession> findByIdWithLock(@Param("id") UUID id);
}
