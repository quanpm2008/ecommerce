package com.example.product_service.product.validator;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Normalizes Vietnamese text for accent- and case-insensitive search (P-37).
 */
public final class VietnameseTextNormalizer {

    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");

    private VietnameseTextNormalizer() {
    }

    public static String normalize(String input) {
        if (input == null) {
            return "";
        }
        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        String decomposed = Normalizer.normalize(trimmed, Normalizer.Form.NFD);
        String stripped = COMBINING_MARKS.matcher(decomposed).replaceAll("");
        return stripped
                .replace('đ', 'd')
                .replace('Đ', 'd')
                .toLowerCase(Locale.ROOT);
    }
}
