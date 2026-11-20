package com.example.order_service.checkout;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Component
public class CheckoutQuote {
	private static final Duration TTL = Duration.ofMinutes(5);
	private static final String PURPOSE = "CHECKOUT_QUOTE";
	private static final String SOURCE = "BUY_NOW";
	private static final int VERSION = 1;
	private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
	private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
	private final JsonMapper json;
	private final Clock clock;
	private final SecretKeySpec key;

	@Autowired
	CheckoutQuote(JsonMapper json, ObjectProvider<Clock> clock,
			@Value("${order.checkout.quote-key:}") String quoteKey) {
		this(json, clock.getIfAvailable(Clock::systemUTC), quoteKey);
	}

	public CheckoutQuote(JsonMapper json, Clock clock, String quoteKey) {
		this.json = json;
		this.clock = clock;
		byte[] bytes = quoteKey.isBlank() ? null : Base64.getDecoder().decode(quoteKey);
		if (bytes != null && bytes.length < 32) {
			throw new IllegalArgumentException("order.checkout.quote-key must be at least 32 bytes in Base64");
		}
		key = bytes == null ? null : new SecretKeySpec(bytes, "HmacSHA256");
	}

	public String issue(UUID customerId, UUID productId, UUID sellerId, int quantity, long unitPriceVnd) {
		Instant issued = clock.instant();
		var claims = new Claims(VERSION, PURPOSE, customerId, UUID.randomUUID(), SOURCE, productId, sellerId,
				quantity, unitPriceVnd, Math.multiplyExact(unitPriceVnd, quantity), issued, issued.plus(TTL));
		String body = ENCODER.encodeToString(json.writeValueAsBytes(claims));
		return body + "." + ENCODER.encodeToString(sign(body));
	}

	public Claims verify(String token, UUID customerId) {
		if (token == null) {
			throw new Invalid();
		}
		try {
			String[] pieces = token.split("\\.", -1);
			if (pieces.length != 2 || !MessageDigest.isEqual(sign(pieces[0]), DECODER.decode(pieces[1]))) {
				throw new Invalid();
			}
			Claims claims = json.readValue(DECODER.decode(pieces[0]), Claims.class);
			if (claims == null || claims.version() != VERSION || !PURPOSE.equals(claims.purpose())
					|| !customerId.equals(claims.customerId()) || !SOURCE.equals(claims.source())
					|| claims.checkoutIntentId() == null || claims.productId() == null || claims.sellerId() == null
					|| claims.quantity() < 1 || claims.quantity() > 99 || claims.unitPriceVnd() <= 0
					|| claims.totalAmountVnd() != Math.multiplyExact(claims.unitPriceVnd(), claims.quantity())
					|| claims.issuedAt() == null || claims.expiresAt() == null
					|| !claims.issuedAt().plus(TTL).equals(claims.expiresAt())) {
				throw new Invalid();
			}
			return claims;
		} catch (JacksonException | IllegalArgumentException | ArithmeticException invalid) {
			throw new Invalid();
		}
	}

	public boolean isExpired(Claims claims) {
		return !clock.instant().isBefore(claims.expiresAt());
	}

	public String requestHash(Claims claims, UUID addressId) {
		String canonical = String.join("|", "1", claims.checkoutIntentId().toString(), claims.source(),
				claims.productId().toString(), claims.sellerId().toString(), Integer.toString(claims.quantity()),
				Long.toString(claims.unitPriceVnd()), Long.toString(claims.totalAmountVnd()), addressId.toString());
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest(canonical.getBytes(StandardCharsets.UTF_8)));
		} catch (GeneralSecurityException failure) {
			throw new IllegalStateException(failure);
		}
	}

	private byte[] sign(String body) {
		if (key == null) {
			throw new Unavailable();
		}
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(key);
			return mac.doFinal(body.getBytes(StandardCharsets.US_ASCII));
		} catch (GeneralSecurityException failure) {
			throw new IllegalStateException(failure);
		}
	}

	public record Claims(int version, String purpose, UUID customerId, UUID checkoutIntentId, String source,
			UUID productId, UUID sellerId, int quantity, long unitPriceVnd, long totalAmountVnd,
			Instant issuedAt, Instant expiresAt) {
	}

	public static class Invalid extends RuntimeException {
	}

	public static class Unavailable extends RuntimeException {
	}
}
