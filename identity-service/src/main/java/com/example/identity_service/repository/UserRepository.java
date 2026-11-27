package com.example.identity_service.repository;

import com.example.identity_service.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

	/** {@code email} must already be normalized with {@link User#normalizeEmail}. */
	Optional<User> findByEmail(String email);

	Optional<User> findByGoogleSubject(String googleSubject);

	boolean existsByEmail(String email);

	/** {@code phoneNumber} must already be normalized with {@link PhoneNumbers#normalize}. */
	boolean existsByPhoneNumber(String phoneNumber);

	boolean existsByPhoneNumberAndUserIdNot(String phoneNumber, UUID userId);

	/** Serializes admin actions on one user (review, ban, unban), so concurrent admins cannot both succeed. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select u from User u where u.userId = :userId")
	Optional<User> findLockedById(UUID userId);

	/** Locks the user whose current refresh token this is, so two refreshes with it cannot both rotate it. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<User> findByRefreshTokenHash(String refreshTokenHash);

	/** The owner of a current refresh token, without locking anything. */
	@Query("select u.userId from User u where u.refreshTokenHash = :refreshTokenHash")
	Optional<UUID> findUserIdByRefreshTokenHash(String refreshTokenHash);

	/** Accounts in a ban episode (BANNED or DISABLED), for J7. */
	@Query("select u.userId from User u where u.bannedAt is not null order by u.bannedAt")
	java.util.List<UUID> findIdsOfBannedAccounts();

}
