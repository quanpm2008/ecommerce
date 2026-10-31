package com.example.identity_service.common.persistence;

import java.util.Locale;

/**
 * Search text to a lower-case {@code like} pattern for queries written with {@code escape '\'}.
 */
public final class LikePattern {

	private LikePattern() {
	}

	/** Blank matches everything; otherwise a literal substring ({@code %}, {@code _} and {@code \} escaped). */
	public static String contains(String q) {
		if (q == null || q.isBlank()) {
			return "%";
		}
		String literal = q.strip().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%")
				.replace("_", "\\_");
		return "%" + literal + "%";
	}

}
