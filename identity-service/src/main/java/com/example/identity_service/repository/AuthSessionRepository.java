package com.example.identity_service.repository;

import com.example.identity_service.entity.AuthSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {

	/** The owner of a current refresh token, without locking anything. */
	@Query("select s.userId from AuthSession s where s.refreshTokenHash = :refreshTokenHash")
	Optional<UUID> findUserIdByRefreshTokenHash(String refreshTokenHash);

	/** Locks the session so two refreshes with the same token cannot both rotate it. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<AuthSession> findByRefreshTokenHash(String refreshTokenHash);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from AuthSession s where s.sessionId = :sessionId")
	Optional<AuthSession> findLockedById(UUID sessionId);

	@Modifying(flushAutomatically = true)
	@Query("update AuthSession s set s.revokedAt = :now where s.userId = :userId and s.revokedAt is null")
	int revokeAllOfUser(UUID userId, Instant now);

}
