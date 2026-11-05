package com.example.identity_service.user;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

	/** {@code email} must already be normalized with {@link User#normalizeEmail}. */
	Optional<User> findByEmail(String email);

	boolean existsByEmail(String email);

	/** {@code phoneNumber} must already be normalized with {@link PhoneNumbers#normalize}. */
	boolean existsByPhoneNumber(String phoneNumber);

	boolean existsByPhoneNumberAndUserIdNot(String phoneNumber, UUID userId);

	/** Serializes admin actions on one user (review, ban, unban), so concurrent admins cannot both succeed. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select u from User u where u.userId = :userId")
	Optional<User> findLockedById(UUID userId);

}
