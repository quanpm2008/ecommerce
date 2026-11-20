package com.example.order_service;

import com.example.order_service.checkout.CheckoutQuote;
import com.example.order_service.checkout.CheckoutQuote.Claims;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
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
		assertThat(quotes.requestHash(quotes.verify(priceChangedToken, customer), address)).isNotEqualTo(quotes.requestHash(claims, address));
		assertThat(quotes.requestHash(claims, UUID.randomUUID())).isNotEqualTo(quotes.requestHash(claims, address));
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
