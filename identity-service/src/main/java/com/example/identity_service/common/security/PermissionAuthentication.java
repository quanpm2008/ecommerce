package com.example.identity_service.common.security;

import com.example.identity_service.rbac.PermissionResolver;
import com.example.identity_service.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Makes the caller's current permissions (read from the database, never from the token) the request's authorities,
 * so endpoints check them with {@code @PreAuthorize("hasAuthority('account:update:own')")}. Deliberately not a
 * Spring {@code Converter} bean: those are pulled into {@code @WebMvcTest} slices, which have no repositories.
 */
@Component
@RequiredArgsConstructor
public class PermissionAuthentication {

	private final UserRepository users;

	private final PermissionResolver permissions;

	public JwtAuthenticationToken authenticate(Jwt jwt) {
		List<SimpleGrantedAuthority> authorities = users.findById(UUID.fromString(jwt.getSubject()))
				.map(permissions::permissionsOf)
				.stream()
				.flatMap(codes -> codes.stream().map(SimpleGrantedAuthority::new))
				.toList();
		return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
	}

}
