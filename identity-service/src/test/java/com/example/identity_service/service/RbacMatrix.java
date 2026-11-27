package com.example.identity_service.service;

import com.example.identity_service.entity.RoleCode;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The permission matrix of API v1.1 §3.2.1, written out independently of the seed so tests compare against the spec.
 */
final class RbacMatrix {

	static final Set<String> CUSTOMER = Set.of("customer:profile:own", "cart:manage:own", "checkout:create:own",
			"payment:create:own", "order:read:own", "order:cancel:own", "order:confirm:own", "refund:retry:own",
			"review:write:own", "notification:read:own");

	static final Set<String> SELLER = Set.of("seller:profile:own", "product:manage:own", "inventory:manage:own",
			"order:fulfill:own", "wallet:read:own", "withdrawal:create:own", "notification:read:own");

	static final Set<String> ADMIN = Set.of("seller:approve", "account:moderate", "product:moderate",
			"category:manage", "operations:read:any", "operations:annotate", "notification:read:own");

	/** Every ACTIVE account, on top of its role. */
	static final Set<String> BASELINE = Set.of("account:read:own", "account:update:own");

	static final Map<String, Set<String>> BY_ROLE = Map.of("CUSTOMER", CUSTOMER, "SELLER", SELLER, "ADMIN", ADMIN);

	static Set<String> of(RoleCode role) {
		return BY_ROLE.get(role.name());
	}

	private RbacMatrix() {
	}

	@SafeVarargs
	static Set<String> union(Set<String>... sets) {
		Set<String> all = new TreeSet<>();
		for (Set<String> set : sets) {
			all.addAll(set);
		}
		return all;
	}

}
