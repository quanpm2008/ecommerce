package com.example.identity_service.rbac;

import com.example.identity_service.audit.AuditAction;
import com.example.identity_service.audit.AuditState;
import com.example.identity_service.audit.AuditTargetType;
import com.example.identity_service.audit.AuditTrail;
import com.example.identity_service.outbox.EventActor;
import com.example.identity_service.outbox.OutboxWriter;
import com.example.identity_service.user.User;
import com.example.identity_service.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The only way to change a user's roles. Every effective change, in the same transaction:
 * <ul>
 * <li>bumps {@code authzVersion} (API §3.2.1), so tokens issued before it become stale;</li>
 * <li>writes {@code user.roles_changed} with the current role codes (API §7.2);</li>
 * <li>writes a {@code GRANT_ROLE} / {@code REVOKE_ROLE} audit row when an admin made the change (DB §6). Changes the
 * user makes for themselves (registration, adding a Customer profile) are not admin actions.</li>
 * </ul>
 * Repeating a grant or revoke changes nothing and writes nothing.
 */
@Service
@RequiredArgsConstructor
public class RoleAssignments {

	private final RoleRepository roles;

	private final UserRoleRepository userRoles;

	private final UserRepository users;

	private final OutboxWriter outbox;

	private final AuditTrail audit;

	/**
	 * Grants the role, reusing a revoked row. Returns false if the role is already active. {@code grantedBy} is the
	 * admin who granted it, or null when the user or the system did (registration, bootstrap).
	 */
	@Transactional
	public boolean grant(User user, RoleCode code, UUID grantedBy) {
		Role role = roles.findByCode(code).orElseThrow();
		Optional<UserRole> existing = userRoles.findById(new UserRoleId(user.getUserId(), role.getRoleId()));
		if (existing.isPresent() && existing.get().isActive()) {
			return false;
		}
		Set<RoleCode> before = activeRoles(user);
		existing.ifPresentOrElse(userRole -> userRole.regrant(grantedBy),
				() -> userRoles.save(new UserRole(user.getUserId(), role, grantedBy)));
		Set<RoleCode> after = withRole(before, code);
		changed(user, AuditAction.GRANT_ROLE, grantedBy, before, after);
		return true;
	}

	/** Revokes the role as the system; see {@link #revoke(User, RoleCode, UUID)}. */
	@Transactional
	public boolean revoke(User user, RoleCode code) {
		return revoke(user, code, null);
	}

	/** Revokes the role. Returns false if the user does not hold it. {@code revokedBy} is the admin, or null. */
	@Transactional
	public boolean revoke(User user, RoleCode code, UUID revokedBy) {
		Role role = roles.findByCode(code).orElseThrow();
		Optional<UserRole> existing = userRoles.findById(new UserRoleId(user.getUserId(), role.getRoleId()))
				.filter(UserRole::isActive);
		if (existing.isEmpty()) {
			return false;
		}
		Set<RoleCode> before = activeRoles(user);
		existing.get().revoke();
		Set<RoleCode> after = EnumSet.noneOf(RoleCode.class);
		before.stream().filter(held -> held != code).forEach(after::add);
		changed(user, AuditAction.REVOKE_ROLE, revokedBy, before, after);
		return true;
	}

	/** {@code user} may be detached; the bump is saved either way. */
	private void changed(User user, AuditAction action, UUID adminId, Set<RoleCode> before, Set<RoleCode> after) {
		AuditState stateBefore = AuditState.roles(user, before);
		user.bumpAuthzVersion();
		users.save(user);
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("userId", user.getUserId());
		payload.put("roleCodes", after.stream().map(Enum::name).sorted().toList());
		payload.put("authzVersion", user.getAuthzVersion());
		EventActor actor = adminId != null ? EventActor.user(adminId, EventActor.Context.ADMIN)
				: EventActor.user(user.getUserId(), EventActor.Context.ACCOUNT);
		outbox.write("user.roles_changed", user.getUserId(), actor, payload);
		if (adminId != null) {
			audit.record(adminId, action, AuditTargetType.USER, user.getUserId(), null, stateBefore,
					AuditState.roles(user, after));
		}
	}

	private Set<RoleCode> activeRoles(User user) {
		Set<RoleCode> active = EnumSet.noneOf(RoleCode.class);
		active.addAll(userRoles.findActiveRoleCodes(user.getUserId()));
		return active;
	}

	private static Set<RoleCode> withRole(Set<RoleCode> roles, RoleCode code) {
		Set<RoleCode> copy = EnumSet.noneOf(RoleCode.class);
		copy.addAll(roles);
		copy.add(code);
		return copy;
	}

}
