package com.example.identity_service.audit;

import com.example.identity_service.customer.Customer;
import com.example.identity_service.seller.Seller;
import com.example.identity_service.user.User;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The access-relevant state of one account, recorded before and after an admin action. Only statuses and the
 * authorization version: by construction no token, password or reset URL can reach the audit log (DB §6).
 */
public record AuditState(String userStatus, String customerStatus, String banMode, Boolean hasPaidOrderBeforeBan,
		String sellerStatus, String approvalStatus, long authzVersion) {

	/** {@code customer} and {@code seller} may be null when the user has no such profile. */
	public static AuditState capture(User user, Customer customer, Seller seller) {
		return new AuditState(user.getStatus().name(),
				customer == null ? null : customer.getStatus().name(),
				customer == null || customer.getBanMode() == null ? null : customer.getBanMode().name(),
				customer == null ? null : customer.getHasPaidOrderBeforeBan(),
				seller == null ? null : seller.getStatus().name(),
				seller == null ? null : seller.getApprovalStatus().name(), user.getAuthzVersion());
	}

	Map<String, Object> asMap() {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("userStatus", userStatus);
		putIfPresent(map, "customerStatus", customerStatus);
		putIfPresent(map, "banMode", banMode);
		putIfPresent(map, "hasPaidOrderBeforeBan", hasPaidOrderBeforeBan);
		putIfPresent(map, "sellerStatus", sellerStatus);
		putIfPresent(map, "approvalStatus", approvalStatus);
		map.put("authzVersion", authzVersion);
		return map;
	}

	private static void putIfPresent(Map<String, Object> map, String key, Object value) {
		if (value != null) {
			map.put(key, value);
		}
	}

}
