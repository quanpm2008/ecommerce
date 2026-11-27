package com.example.identity_service.repository;

import com.example.identity_service.entity.RotatedRefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface RotatedRefreshTokenRepository extends JpaRepository<RotatedRefreshToken, String> {

	/** Marks of the user's earlier sessions: they can no longer revoke anything (decision 0003). */
	@Modifying(flushAutomatically = true)
	@Query("delete from RotatedRefreshToken r where r.userId = :userId and r.sessionId <> :sessionId")
	int deleteOtherSessionsOf(UUID userId, UUID sessionId);

	@Modifying(flushAutomatically = true)
	@Query("delete from RotatedRefreshToken r where r.userId = :userId")
	int deleteAllOf(UUID userId);

}
