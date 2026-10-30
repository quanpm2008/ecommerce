package com.example.identity_service.rbac;

import com.example.identity_service.user.User;
import com.example.identity_service.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * The only way to change a user's roles. Every effective change bumps {@code authzVersion} in the same transaction
 * (API §3.2.1), so tokens issued before it become stale. Callers write the audit row and outbox event.
 */
@Service
@RequiredArgsConstructor
public class RoleAssignments {

	private final RoleRepository roles;

	private final UserRoleRepository userRoles;

	private final UserRepository users;

	/**
	 * Grants the role, reusing a revoked row. Returns false, with no version bump, if the role is already active.
	 * {@code grantedBy} is null for system grants.
	 */
	@Transactional
	public boolean grant(User user, RoleCode code, UUID grantedBy) {
		Role role = roles.findByCode(code).orElseThrow();
		Optional<UserRole> existing = userRoles.findById(new UserRoleId(user.getUserId(), role.getRoleId()));
		if (existing.isPresent() && existing.get().isActive()) {
			return false;
		}
		existing.ifPresentOrElse(userRole -> userRole.regrant(grantedBy),
				() -> userRoles.save(new UserRole(user.getUserId(), role, grantedBy)));
		bump(user);
		return true;
	}

	/** Revokes the role. Returns false, with no version bump, if the user does not hold it. */
	@Transactional
	public boolean revoke(User user, RoleCode code) {
		Role role = roles.findByCode(code).orElseThrow();
		Optional<UserRole> existing = userRoles.findById(new UserRoleId(user.getUserId(), role.getRoleId()))
				.filter(UserRole::isActive);
		if (existing.isEmpty()) {
			return false;
		}
		existing.get().revoke();
		bump(user);
		return true;
	}

	/** {@code user} may be detached; the bump is saved either way. */
	private void bump(User user) {
		user.bumpAuthzVersion();
		users.save(user);
	}

}
