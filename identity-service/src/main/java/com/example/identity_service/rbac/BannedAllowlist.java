package com.example.identity_service.rbac;

import com.example.identity_service.user.AccessMode;
import com.example.identity_service.user.AccountType;

import java.util.Map;
import java.util.Set;

/**
 * What a BANNED account keeps of its role's permissions (API §3.2 G-14 table, §3.2.1): only what its own open
 * obligations need, by {@link AccessMode}, plus reading its own account. The owning services still check ownership and
 * state on every call ({@code accessMode} never opens another account's orders).
 */
final class BannedAllowlist {

	private static final Map<AccessMode, Set<String>> CUSTOMER = Map.of(
			AccessMode.OPEN_ORDERS_ONLY, Set.of("order:read:own", "order:cancel:own", "order:confirm:own",
					"refund:retry:own", "notification:read:own"),
			AccessMode.OBLIGATIONS_READ_ONLY, Set.of("order:read:own", "notification:read:own"));

	private static final Set<String> SELLER_ORDERS = Set.of("order:fulfill:own", "notification:read:own");

	private static final Set<String> SELLER_WITHDRAWAL = Set.of("wallet:read:own", "withdrawal:create:own",
			"notification:read:own");

	private static final Map<AccessMode, Set<String>> SELLER = Map.of(
			AccessMode.OPEN_ORDERS_ONLY, SELLER_ORDERS,
			AccessMode.WITHDRAWAL_ONLY, SELLER_WITHDRAWAL,
			AccessMode.ORDERS_AND_WITHDRAWAL, union(SELLER_ORDERS, SELLER_WITHDRAWAL),
			AccessMode.OBLIGATIONS_READ_ONLY, Set.of("wallet:read:own", "notification:read:own"));

	private BannedAllowlist() {
	}

	static Set<String> of(AccountType accountType, AccessMode mode) {
		Map<AccessMode, Set<String>> table = accountType == AccountType.SELLER ? SELLER
				: accountType == AccountType.CUSTOMER ? CUSTOMER : Map.of();
		return union(Set.of(Permissions.ACCOUNT_READ_OWN), table.getOrDefault(mode, Set.of()));
	}

	private static Set<String> union(Set<String> a, Set<String> b) {
		java.util.Set<String> all = new java.util.HashSet<>(a);
		all.addAll(b);
		return Set.copyOf(all);
	}

}
