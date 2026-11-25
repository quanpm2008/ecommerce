package com.example.order_service.service;

import com.example.order_service.client.CartCheckoutItemsClient;
import com.example.order_service.client.CartCheckoutItemsClient.Line;
import com.example.order_service.client.CartCheckoutItemsClient.Snapshot;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.StringJoiner;
import java.util.UUID;

@Component
public class CheckoutQuote {
	private static final Duration TTL = Duration.ofMinutes(5);
	private static final String PURPOSE = "CHECKOUT_QUOTE";
	private static final String SOURCE = "BUY_NOW";
	private static final int VERSION = 1;
	private static final String CART_SOURCE = "CART";
	private static final int CART_VERSION = 2;
	private static final Comparator<CartLine> CART_LINE_ORDER = Comparator.comparing(line -> line.cartItemId().toString());
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
		return encode(claims);
	}

	public String issueCart(UUID customerId, UUID cartId, List<CartLine> items) {
		Instant issued = clock.instant();
		var canonicalItems = items.stream().sorted(CART_LINE_ORDER).toList();
		return encode(new CartClaims(CART_VERSION, PURPOSE, customerId, UUID.randomUUID(), CART_SOURCE, cartId,
				canonicalItems, cartTotal(canonicalItems), issued, issued.plus(TTL)));
	}

	private String encode(VerifiedQuote claims) {
		String body = ENCODER.encodeToString(json.writeValueAsBytes(claims));
		return body + "." + ENCODER.encodeToString(sign(body));
	}

	public Claims verify(String token, UUID customerId) {
		if (verifyAny(token, customerId) instanceof Claims claims) {
			return claims;
		}
		throw new Invalid();
	}

	public CartClaims verifyCart(String token, UUID customerId) {
		if (verifyAny(token, customerId) instanceof CartClaims claims) {
			return claims;
		}
		throw new Invalid();
	}

	public VerifiedQuote verifyAny(String token, UUID customerId) {
		if (token == null) {
			throw new Invalid();
		}
		try {
			String[] pieces = token.split("\\.", -1);
			if (pieces.length != 2 || !MessageDigest.isEqual(sign(pieces[0]), DECODER.decode(pieces[1]))) {
				throw new Invalid();
			}
			JsonNode payload = json.readTree(DECODER.decode(pieces[0]));
			var version = payload.path("version");
			String source = payload.path("source").asString();
			if (!version.isIntegralNumber() || !version.canConvertToInt()) {
				throw new Invalid();
			}
			VerifiedQuote claims;
			if (version.asInt() == VERSION && SOURCE.equals(source)) {
				claims = readBuyNow(payload);
			} else if (version.asInt() == CART_VERSION && CART_SOURCE.equals(source)) {
				claims = readCart(payload);
			} else {
				throw new Invalid();
			}
			if (!PURPOSE.equals(claims.purpose()) || !customerId.equals(claims.customerId())
					|| claims.checkoutIntentId() == null || claims.issuedAt() == null || claims.expiresAt() == null
					|| !claims.issuedAt().plus(TTL).equals(claims.expiresAt())) {
				throw new Invalid();
			}
			return claims;
		} catch (JacksonException | IllegalArgumentException | ArithmeticException | DateTimeException invalid) {
			throw new Invalid();
		}
	}

	private Claims readBuyNow(JsonNode payload) {
		Claims claims = json.treeToValue(payload, Claims.class);
		if (claims.productId() == null || claims.sellerId() == null || claims.quantity() < 1 || claims.quantity() > 99
				|| claims.unitPriceVnd() <= 0
				|| claims.totalAmountVnd() != Math.multiplyExact(claims.unitPriceVnd(), claims.quantity())) {
			throw new Invalid();
		}
		return claims;
	}

	private CartClaims readCart(JsonNode payload) {
		var items = payload.path("items");
		if (!items.isArray() || items.isEmpty() || items.size() > CartCheckoutItemsClient.MAX_LINES
				|| !isLong(payload.path("totalAmountVnd"))) {
			throw new Invalid();
		}
		for (var line : items) {
			if (!line.path("quantity").isIntegralNumber() || !line.path("quantity").canConvertToInt()
					|| !isLong(line.path("rowVersion")) || !isLong(line.path("unitPriceVnd"))) {
				throw new Invalid();
			}
		}
		CartClaims claims = json.treeToValue(payload, CartClaims.class);
		if (claims.cartId() == null || claims.totalAmountVnd() != cartTotal(claims.items())) {
			throw new Invalid();
		}
		return claims;
	}

	private static boolean isLong(JsonNode number) {
		return number.isIntegralNumber() && number.canConvertToLong();
	}

	private long cartTotal(List<CartLine> items) {
		if (items.isEmpty() || items.size() > CartCheckoutItemsClient.MAX_LINES) {
			throw new Invalid();
		}
		long total = 0;
		CartLine previous = null;
		for (var line : items) {
			if (line == null || line.cartItemId() == null || line.productId() == null || line.sellerId() == null
					|| line.quantity() < 1 || line.quantity() > 99 || line.rowVersion() < 0 || line.unitPriceVnd() <= 0
					|| previous != null && CART_LINE_ORDER.compare(previous, line) >= 0) {
				throw new Invalid();
			}
			total = Math.addExact(total, Math.multiplyExact(line.unitPriceVnd(), line.quantity()));
			previous = line;
		}
		return total;
	}

	public boolean isExpired(VerifiedQuote claims) {
		return !clock.instant().isBefore(claims.expiresAt());
	}

	public String requestHash(Claims claims, UUID addressId) {
		String canonical = String.join("|", "1", claims.checkoutIntentId().toString(), claims.source(),
				claims.productId().toString(), claims.sellerId().toString(), Integer.toString(claims.quantity()),
				Long.toString(claims.unitPriceVnd()), Long.toString(claims.totalAmountVnd()), addressId.toString());
		return hash(canonical);
	}

	public String requestHash(CartClaims claims, UUID addressId) {
		var canonical = new StringJoiner("|");
		canonical.add(Integer.toString(CART_VERSION)).add(claims.checkoutIntentId().toString()).add(CART_SOURCE)
				.add(claims.customerId().toString()).add(claims.cartId().toString())
				.add(Long.toString(claims.totalAmountVnd())).add(addressId.toString());
		for (var line : claims.items()) {
			canonical.add(line.cartItemId().toString()).add(line.productId().toString())
					.add(Integer.toString(line.quantity())).add(Long.toString(line.rowVersion()))
					.add(line.sellerId().toString()).add(Long.toString(line.unitPriceVnd()));
		}
		return hash(canonical.toString());
	}

	private String hash(String canonical) {
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

	public sealed interface VerifiedQuote permits Claims, CartClaims {
		String purpose();
		UUID customerId();
		UUID checkoutIntentId();
		String source();
		Instant issuedAt();
		Instant expiresAt();
	}

	public record Claims(int version, String purpose, UUID customerId, UUID checkoutIntentId, String source,
			UUID productId, UUID sellerId, int quantity, long unitPriceVnd, long totalAmountVnd,
			Instant issuedAt, Instant expiresAt) implements VerifiedQuote {
	}

	public record CartClaims(int version, String purpose, UUID customerId, UUID checkoutIntentId, String source,
			UUID cartId, List<CartLine> items, long totalAmountVnd, Instant issuedAt, Instant expiresAt) implements VerifiedQuote {
		public CartClaims {
			items = List.copyOf(items);
		}

		public boolean matches(Snapshot snapshot) {
			return cartId.equals(snapshot.cartId()) && customerId.equals(snapshot.customerId())
					&& items.stream().map(CartLine::snapshotLine).toList().equals(snapshot.items().stream()
							.sorted(Comparator.comparing(line -> line.cartItemId().toString())).toList());
		}
	}

	public record CartLine(UUID cartItemId, UUID productId, int quantity, long rowVersion, UUID sellerId, long unitPriceVnd) {
		private Line snapshotLine() {
			return new Line(cartItemId, productId, quantity, rowVersion);
		}
	}

	public static class Invalid extends RuntimeException {
	}

	public static class Unavailable extends RuntimeException {
	}
}
