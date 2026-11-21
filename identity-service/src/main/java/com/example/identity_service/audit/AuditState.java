package com.example.identity_service.audit;

import com.example.identity_service.customer.Customer;
import com.example.identity_service.rbac.RoleCode;
import com.example.identity_service.seller.Seller;
import com.example.identity_service.user.User;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The access-relevant state of one account, recorded before and after an admin action. Only statuses and the
 * authorization version: by construction no token, password or reset URL can reach the audit log (DB §6).
 */
public record AuditState(String userStatus, String customerStatus, String sellerStatus, String approvalStatus,
		String accessMode, List<String> roles, long authzVersion) {

	/** {@code customer} and {@code seller} may be null when the user has no such profile. */
	public static AuditState capture(User user, Customer customer, Seller seller) {
		return new AuditState(user.getStatus().name(),
				customer == null ? null : customer.getStatus().name(),
				seller == null ? null : seller.getStatus().name(),
				seller == null ? null : seller.getApprovalStatus().name(),
				user.getAccessMode() == null ? null : user.getAccessMode().name(), null, user.getAuthzVersion());
	}

	/** For role grants and revokes: the user's active roles. */
	public static AuditState roles(User user, Collection<RoleCode> roles) {
		return new AuditState(user.getStatus().name(), null, null, null, null,
				roles.stream().map(Enum::name).sorted().toList(), user.getAuthzVersion());
	}

	Map<String, Object> asMap() {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("userStatus", userStatus);
		putIfPresent(map, "customerStatus", customerStatus);
		putIfPresent(map, "sellerStatus", sellerStatus);
		putIfPresent(map, "approvalStatus", approvalStatus);
		putIfPresent(map, "accessMode", accessMode);
		putIfPresent(map, "roles", roles);
		map.put("authzVersion", authzVersion);
		return map;
	}

	private static void putIfPresent(Map<String, Object> map, String key, Object value) {
		if (value != null) {
			map.put(key, value);
		}
	}

}
