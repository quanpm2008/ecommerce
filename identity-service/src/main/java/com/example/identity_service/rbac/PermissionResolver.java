package com.example.identity_service.rbac;

import com.example.identity_service.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Effective permissions: the baseline policy plus the union of the user's unrevoked roles, read from the database
 * every time. A DISABLED user has none. Profile status (BANNED, PENDING, ...) and ownership are checked separately
 * by each endpoint.
 */
@Service
@RequiredArgsConstructor
public class PermissionResolver {

	private final UserRoleRepository userRoles;

	@Transactional(readOnly = true)
	public SortedSet<String> permissionsOf(User user) {
		SortedSet<String> permissions = new TreeSet<>();
		if (!user.isActive()) {
			return permissions;
		}
		permissions.addAll(Permissions.BASELINE);
		permissions.addAll(userRoles.findActivePermissionCodes(user.getUserId()));
		return permissions;
	}

	@Transactional(readOnly = true)
	public Set<RoleCode> activeRolesOf(User user) {
		return userRoles.findActiveRoleCodes(user.getUserId());
	}

}
