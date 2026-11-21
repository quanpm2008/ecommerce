package com.example.identity_service.admin;

import com.example.identity_service.common.error.ApiException;
import com.example.identity_service.common.error.ErrorCode;

import java.util.List;

/**
 * Admin decision reasons (P-21d, DB §3.1.2/§3.1.3): each action has its own list of labels, and a reason is a label or
 * {@code "Label: description"}, 1–1,000 characters in total. "Khác" (Other) needs a non-blank description. No enum or
 * reason code: the text is stored as given (trimmed, one space after the colon). The labels are the spec's own words.
 */
public final class AdminReasons {

	static final String OTHER = "Khác";

	/** Rejecting a seller application. */
	public static final List<String> SELLER_REJECTION = List.of("Thông tin đăng ký không chính xác/không nhất quán",
			"Thông tin gian hàng không phù hợp", OTHER);

	/** Banning a seller account; the two trade reasons are for sellers only. */
	public static final List<String> SELLER_BAN = List.of("Gian lận/lừa đảo", "Kinh doanh hàng cấm",
			"Kinh doanh hàng giả/vi phạm sở hữu trí tuệ", OTHER);

	/** Banning a customer account. */
	public static final List<String> CUSTOMER_BAN = List.of("Gian lận/lừa đảo", OTHER);

	private static final int MAX_LENGTH = 1000;

	private AdminReasons() {
	}

	/** The normalized reason, or 400 {@code VALIDATION_ERROR} on {@code reason}. */
	public static String check(String reason, List<String> labels) {
		String text = reason == null ? "" : reason.strip();
		for (String label : labels) {
			if (text.equals(label)) {
				if (label.equals(OTHER)) {
					throw invalid("DESCRIPTION_REQUIRED");
				}
				return label;
			}
			if (text.startsWith(label + ":")) {
				String description = text.substring(label.length() + 1).strip();
				if (description.isEmpty()) {
					if (label.equals(OTHER)) {
						throw invalid("DESCRIPTION_REQUIRED");
					}
					return label;
				}
				String normalized = label + ": " + description;
				if (normalized.length() > MAX_LENGTH) {
					throw invalid("TOO_LONG");
				}
				return normalized;
			}
		}
		throw invalid(text.isEmpty() ? "REQUIRED" : "UNKNOWN_REASON");
	}

	private static ApiException invalid(String issue) {
		return ApiException.forField(ErrorCode.VALIDATION_ERROR, "reason", issue);
	}

}
