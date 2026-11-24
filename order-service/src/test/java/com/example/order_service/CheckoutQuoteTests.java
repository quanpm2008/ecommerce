package com.example.order_service;

import com.example.order_service.client.CartCheckoutItemsClient.Line;
import com.example.order_service.client.CartCheckoutItemsClient.Snapshot;
import com.example.order_service.service.CheckoutQuote;
import com.example.order_service.service.CheckoutQuote.CartClaims;
import com.example.order_service.service.CheckoutQuote.CartLine;
import com.example.order_service.service.CheckoutQuote.Claims;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CheckoutQuoteTests {
	static final String QUOTE_KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
	private final Clock issuance = Clock.fixed(Instant.parse("2026-11-21T01:00:00Z"), ZoneOffset.UTC);
	private final JsonMapper json = JsonMapper.builder().findAndAddModules().build();
	private final CheckoutQuote quotes = new CheckoutQuote(json, issuance, QUOTE_KEY);
	private final UUID customer = UUID.randomUUID();
	private final UUID product = UUID.randomUUID();
	private final UUID seller = UUID.randomUUID();

	@Test
	void checksFiveMinuteBoundarySeparatelyFromIntegrity() {
		String token = quotes.issue(customer, product, seller, 2, 1500);
		Claims claims = quotes.verify(token, customer);
		assertThat(claims.issuedAt()).isEqualTo(issuance.instant());
		assertThat(claims.expiresAt()).isEqualTo(issuance.instant().plusSeconds(300));
		assertThat(claims.totalAmountVnd()).isEqualTo(3000);
		assertThat(atOffset(299).isExpired(claims)).isFalse();
		CheckoutQuote boundary = atOffset(300);
		assertThat(boundary.isExpired(boundary.verify(token, customer))).isTrue();
	}

	@Test
	void rejectsTamperedAndForeignCustomerClaims() {
		String token = quotes.issue(customer, product, seller, 2, 1500);
		String[] segments = token.split("\\.");
		byte[] original = Base64.getUrlDecoder().decode(segments[0]);
		original[1] ^= 1;
		String tampered = Base64.getUrlEncoder().withoutPadding().encodeToString(original) + "." + segments[1];
		assertThatThrownBy(() -> quotes.verify(tampered, customer)).isInstanceOf(CheckoutQuote.Invalid.class);
		assertThatThrownBy(() -> quotes.verify(token, UUID.randomUUID())).isInstanceOf(CheckoutQuote.Invalid.class);
	}

	@Test
	void hashesBusinessContentAndAddressIndependentlyOfIssueTimes() throws Exception {
		Claims claims = quotes.verify(quotes.issue(customer, product, seller, 2, 1500), customer);
		UUID address = UUID.randomUUID();
		Claims reissued = new Claims(claims.version(), claims.purpose(), customer, claims.checkoutIntentId(), claims.source(),
				product, seller, 2, 1500, 3000, claims.issuedAt().plusSeconds(60), claims.expiresAt().plusSeconds(60));
		Claims priceChanged = new Claims(claims.version(), claims.purpose(), customer, claims.checkoutIntentId(), claims.source(),
				product, seller, 2, 1600, 3200, claims.issuedAt(), claims.expiresAt());
		String reissuedToken = sign(json.writerWithDefaultPrettyPrinter().writeValueAsBytes(reissued));
		String priceChangedToken = sign(json.writeValueAsBytes(priceChanged));
		assertThat(quotes.requestHash(quotes.verify(reissuedToken, customer), address)).isEqualTo(quotes.requestHash(claims, address));
		String v1Canonical = String.join("|", "1", claims.checkoutIntentId().toString(), "BUY_NOW",
				product.toString(), seller.toString(), "2", "1500", "3000", address.toString());
		String v1Hash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
				.digest(v1Canonical.getBytes(StandardCharsets.UTF_8)));
		assertThat(quotes.requestHash(claims, address)).isEqualTo(v1Hash);
		assertThat(quotes.requestHash(quotes.verify(priceChangedToken, customer), address)).isNotEqualTo(quotes.requestHash(claims, address));
		assertThat(quotes.requestHash(claims, UUID.randomUUID())).isNotEqualTo(quotes.requestHash(claims, address));
	}

	@Test
	void cartQuoteBindsTheCanonicalSnapshotAndExpiresAtFiveMinutes() {
		UUID cart = UUID.randomUUID();
		var first = new CartLine(UUID.fromString("00000000-0000-0000-0000-000000000001"), product, 2, 7, seller, 1500);
		var second = new CartLine(UUID.fromString("80000000-0000-0000-0000-000000000002"), UUID.randomUUID(), 1, 0,
				UUID.randomUUID(), 4500);
		String token = quotes.issueCart(customer, cart, List.of(second, first));
		CartClaims claims = quotes.verifyCart(token, customer);
		assertThat(quotes.verifyAny(token, customer)).isEqualTo(claims);
		assertThat(claims.items()).containsExactly(first, second);
		assertThat(claims.totalAmountVnd()).isEqualTo(7500L);
		assertThat(claims.expiresAt()).isEqualTo(issuance.instant().plusSeconds(300));
		assertThat(atOffset(299).isExpired(claims)).isFalse();
		assertThat(atOffset(300).isExpired(claims)).isTrue();
		var one = new Line(first.cartItemId(), first.productId(), first.quantity(), first.rowVersion());
		var two = new Line(second.cartItemId(), second.productId(), second.quantity(), second.rowVersion());
		assertThat(claims.matches(new Snapshot(cart, customer, List.of(two, one)))).isTrue();
		assertThat(claims.matches(new Snapshot(UUID.randomUUID(), customer, List.of(one, two)))).isFalse();
		assertThat(claims.matches(new Snapshot(cart, UUID.randomUUID(), List.of(one, two)))).isFalse();
		assertThat(claims.matches(new Snapshot(cart, customer, List.of(one)))).isFalse();
		assertThat(claims.matches(new Snapshot(cart, customer, List.of(one, one)))).isFalse();
		for (Line changed : List.of(new Line(one.cartItemId(), one.productId(), one.quantity(), one.rowVersion() + 1),
				new Line(one.cartItemId(), one.productId(), one.quantity() + 1, one.rowVersion()),
				new Line(one.cartItemId(), UUID.randomUUID(), one.quantity(), one.rowVersion()))) {
			assertThat(claims.matches(new Snapshot(cart, customer, List.of(changed, two)))).isFalse();
		}
	}

	@Test
	void checkoutQuoteRejectsWrongTypeOwnerOrTamperedCartClaims() throws Exception {
		var line = new CartLine(UUID.randomUUID(), product, 2, 0, seller, 1500);
		String token = quotes.issueCart(customer, UUID.randomUUID(), List.of(line));
		CartClaims claims = quotes.verifyCart(token, customer);
		assertThatThrownBy(() -> quotes.verify(token, customer)).isInstanceOf(CheckoutQuote.Invalid.class);
		assertThatThrownBy(() -> quotes.verifyAny(token, UUID.randomUUID())).isInstanceOf(CheckoutQuote.Invalid.class);
		assertThatThrownBy(() -> quotes.verifyAny(token + ".unexpected", customer)).isInstanceOf(CheckoutQuote.Invalid.class);
		assertRejected(json.valueToTree(claims), "source", "BUY_NOW");
		assertRejected(json.valueToTree(claims), "purpose", "CHECKOUT_PAY");
		ObjectNode invalidVersion = json.valueToTree(claims);
		invalidVersion.put("version", 1);
		assertInvalid(invalidVersion);
		ObjectNode invalidTotal = json.valueToTree(claims);
		invalidTotal.put("totalAmountVnd", 1);
		assertInvalid(invalidTotal);
		ObjectNode missingVersion = json.valueToTree(claims);
		((ObjectNode) missingVersion.at("/items/0")).remove("rowVersion");
		assertInvalid(missingVersion);
		String[] pieces = token.split("\\.");
		byte[] modified = Base64.getUrlDecoder().decode(pieces[0]);
		modified[1] ^= 1;
		String forged = Base64.getUrlEncoder().withoutPadding().encodeToString(modified) + "." + pieces[1];
		assertThatThrownBy(() -> quotes.verifyAny(forged, customer)).isInstanceOf(CheckoutQuote.Invalid.class);
		assertThatThrownBy(() -> quotes.verifyCart(quotes.issue(customer, product, seller, 1, 1), customer))
				.isInstanceOf(CheckoutQuote.Invalid.class);
	}

	@Test
	void cartRequestHashChangesWhenTheLineVersionPriceOrAddressChanges() throws Exception {
		var line = new CartLine(UUID.randomUUID(), product, 2, 7, seller, 1500);
		CartClaims claims = quotes.verifyCart(quotes.issueCart(customer, UUID.randomUUID(), List.of(line)), customer);
		UUID address = UUID.randomUUID();
		String hash = quotes.requestHash(claims, address);
		ObjectNode changedVersion = json.valueToTree(claims);
		((ObjectNode) changedVersion.at("/items/0")).put("rowVersion", 9);
		var changedPrice = new CartClaims(claims.version(), claims.purpose(), customer, claims.checkoutIntentId(),
				claims.source(), claims.cartId(), List.of(new CartLine(line.cartItemId(), product, 2, 7, seller, 2000)),
				4000, claims.issuedAt(), claims.expiresAt());
		assertThat(quotes.requestHash(quotes.verifyCart(sign(json.writeValueAsBytes(changedVersion)), customer), address))
				.isNotEqualTo(hash);
		assertThat(quotes.requestHash(changedPrice, address)).isNotEqualTo(hash);
		assertThat(quotes.requestHash(claims, UUID.randomUUID())).isNotEqualTo(hash);
	}

	private void assertRejected(ObjectNode claims, String field, String value) throws Exception {
		claims.put(field, value);
		assertInvalid(claims);
	}

	private void assertInvalid(ObjectNode claims) throws Exception {
		String signed = sign(json.writeValueAsBytes(claims));
		assertThatThrownBy(() -> quotes.verifyAny(signed, customer)).isInstanceOf(CheckoutQuote.Invalid.class);
	}

	private String sign(byte[] jsonBytes) throws Exception {
		var encoder = Base64.getUrlEncoder().withoutPadding();
		String body = encoder.encodeToString(jsonBytes);
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(Base64.getDecoder().decode(QUOTE_KEY), "HmacSHA256"));
		return body + '.' + encoder.encodeToString(mac.doFinal(body.getBytes(StandardCharsets.US_ASCII)));
	}

	private CheckoutQuote atOffset(long seconds) {
		return new CheckoutQuote(json, Clock.offset(issuance, Duration.ofSeconds(seconds)), QUOTE_KEY);
	}
}
