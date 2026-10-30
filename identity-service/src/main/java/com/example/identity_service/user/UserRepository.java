package com.example.identity_service.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

	/** {@code email} must already be normalized with {@link User#normalizeEmail}. */
	Optional<User> findByEmail(String email);

	boolean existsByEmail(String email);

	/** {@code phoneNumber} must already be normalized with {@link PhoneNumbers#normalize}. */
	boolean existsByPhoneNumber(String phoneNumber);

}
