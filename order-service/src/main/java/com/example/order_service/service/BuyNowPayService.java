package com.example.order_service.service;

import com.example.order_service.client.CartCheckoutItemsClient;
import com.example.order_service.client.CartCheckoutItemsClient.Snapshot;
import com.example.order_service.client.CheckoutIdentityClient;
import com.example.order_service.client.CheckoutIdentityClient.Seller;
import com.example.order_service.client.FinanceCommissionClient;
import com.example.order_service.client.PayIdentityClient;
import com.example.order_service.client.ProductBatchClient;
import com.example.order_service.dto.response.CheckoutResponse;
import com.example.order_service.exception.BuyNowPayRejected;
import com.example.order_service.repository.CheckoutCommitRepository;
import com.example.order_service.repository.CheckoutCommitRepository.Line;
import com.example.order_service.repository.CheckoutReplayRepository;
import com.example.order_service.repository.PendingCheckoutReader;
import com.example.order_service.security.OrderAuthorization.CurrentUser;
import com.example.order_service.service.CheckoutQuote.CartClaims;
import com.example.order_service.service.CheckoutQuote.Claims;
import com.example.order_service.service.CheckoutQuote.VerifiedQuote;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class BuyNowPayService {
	private final CheckoutQuote quote;
	private final CheckoutIdentityClient identity;
	private final PayIdentityClient addresses;
	private final ProductBatchClient products;
	private final FinanceCommissionClient finance;
	private final CheckoutCommitRepository commit;
	private final PendingCheckoutReader pending;
	private final CheckoutReplayRepository replays;
	private final CartCheckoutItemsClient.Reader cart;

	BuyNowPayService(CheckoutQuote quote, CheckoutIdentityClient identity, PayIdentityClient addresses,
			ProductBatchClient products, FinanceCommissionClient finance, CheckoutCommitRepository commit,
			PendingCheckoutReader pending, CheckoutReplayRepository replays, CartCheckoutItemsClient.Reader cart) {
		this.quote = quote;
		this.identity = identity;
		this.addresses = addresses;
		this.products = products;
		this.finance = finance;
		this.commit = commit;
		this.pending = pending;
		this.replays = replays;
		this.cart = cart;
	}

	public PayResult pay(CurrentUser customer, String key, String quoteToken, UUID addressId) {
		var claims = quote.verifyAny(quoteToken, customer.userId());
		String hash = switch (claims) {
			case Claims buyNow -> quote.requestHash(buyNow, addressId);
			case CartClaims cartClaims -> quote.requestHash(cartClaims, addressId);
		};
		CheckoutResponse replay = replays.findOrRegisterAlias(customer.userId(), key, claims.checkoutIntentId(), hash);
		if (replay != null) {
			return new PayResult(replay, true);
		}
		try {
			return new PayResult(freshPay(customer, key, claims, addressId, hash), false);
		} catch (DataIntegrityViolationException | BuyNowPayRejected failure) {
			boolean duplicate = failure instanceof DataIntegrityViolationException && uniqueViolation(failure);
			if (failure instanceof DataIntegrityViolationException && !duplicate) {
				throw failure;
			}
			// A concurrent Pay can commit after the first lookup and before an eligibility or uniqueness check.
			replay = replays.findOrRegisterAlias(customer.userId(), key, claims.checkoutIntentId(), hash);
			if (replay != null) {
				return new PayResult(replay, true);
			}
			if (duplicate && pending.pendingSession(customer.userId()).isPresent()) {
				throw new BuyNowPayRejected(409, "CHECKOUT_PENDING_EXISTS");
			}
			throw failure;
		}
	}

	private CheckoutResponse freshPay(CurrentUser customer, String key, VerifiedQuote claims, UUID addressId,
			String hash) {
		if (quote.isExpired(claims)) {
			throw new BuyNowPayRejected(409, "CHECKOUT_QUOTE_EXPIRED");
		}
		pending.pendingSession(customer.userId()).ifPresent(id -> {
			throw new BuyNowPayRejected(409, "CHECKOUT_PENDING_EXISTS");
		});
		var eligibility = identity.eligibility(customer.userId());
		if (eligibility.authzVersion() != customer.authzVersion()) {
			throw new BuyNowPayRejected(401, "TOKEN_STALE");
		}
		if (eligibility.paymentBlocked() || !"ACTIVE".equals(eligibility.customerStatus())
				|| !"ACTIVE".equals(eligibility.userStatus())) {
			throw new BuyNowPayRejected(403, "CUSTOMER_PAYMENT_BLOCKED");
		}
		if (!eligibility.hasPhone() || eligibility.addressCount() == 0) {
			throw new BuyNowPayRejected(422, "PROFILE_INCOMPLETE");
		}
		if (!eligibility.canCheckout()) {
			throw new BuyNowPayRejected(503, "SERVICE_UNAVAILABLE");
		}
		var shippingAddress = addresses.address(customer.userId(), addressId);
		Snapshot snapshot = claims instanceof CartClaims cartClaims ? readCart(cartClaims) : null;
		List<Line> lines = switch (claims) {
			case Claims buyNow -> List.of(buyNowLine(buyNow));
			case CartClaims cartClaims -> cartLines(cartClaims);
		};
		int commissionBps = finance.commissionBps();
		if (quote.isExpired(claims)) {
			throw new BuyNowPayRejected(409, "CHECKOUT_QUOTE_EXPIRED");
		}
		CheckoutResponse response;
		try {
			response = commit.commit(new CheckoutCommitRepository.NewCheckout(customer.userId(), claims.checkoutIntentId(),
					key, hash, addressId, shippingAddress, commissionBps, lines, snapshot, claims.expiresAt()));
		} catch (CheckoutCommitRepository.StockUnavailable unavailable) {
			throw new BuyNowPayRejected(409, "CHECKOUT_ITEMS_INVALID");
		} catch (CheckoutCommitRepository.QuoteExpired expired) {
			throw new BuyNowPayRejected(409, "CHECKOUT_QUOTE_EXPIRED");
		}
		addresses.touch(customer.userId(), addressId, response.checkoutSessionId());
		return response;
	}

	private Snapshot readCart(CartClaims claims) {
		try {
			var ids = claims.items().stream().map(CheckoutQuote.CartLine::cartItemId).toList();
			var current = cart.read(claims.customerId(), ids);
			if (!claims.matches(current)) {
				throw new BuyNowPayRejected(409, "CHECKOUT_QUOTE_CHANGED");
			}
			return current;
		} catch (CartCheckoutItemsClient.MissingItems missing) {
			throw new BuyNowPayRejected(409, "CHECKOUT_QUOTE_CHANGED");
		} catch (CartCheckoutItemsClient.Unavailable unavailable) {
			throw new BuyNowPayRejected(503, "SERVICE_UNAVAILABLE");
		}
	}

	private Line buyNowLine(Claims claims) {
		var results = products.getProducts(List.of(claims.productId()));
		if (results.isEmpty()) {
			throw new BuyNowPayRejected(409, "CHECKOUT_ITEMS_INVALID");
		}
		return verifiedLine(results.getFirst(), claims.sellerId(), claims.unitPriceVnd(), claims.quantity(), null,
				new HashMap<>());
	}

	private List<Line> cartLines(CartClaims claims) {
		var productsRequested = claims.items().stream().map(CheckoutQuote.CartLine::productId).distinct().toList();
		var catalog = products.getProducts(productsRequested).stream()
				.collect(Collectors.toMap(ProductBatchClient.ProductInfo::productId, Function.identity()));
		if (catalog.size() != productsRequested.size()) {
			throw new BuyNowPayRejected(503, "SERVICE_UNAVAILABLE");
		}
		var sellers = new HashMap<UUID, Seller>();
		var lines = new ArrayList<Line>();
		for (var line : claims.items()) {
			var item = catalog.get(line.productId());
			if (item.priceVnd() <= 0) {
				throw new BuyNowPayRejected(503, "SERVICE_UNAVAILABLE");
			}
			lines.add(verifiedLine(item, line.sellerId(), line.unitPriceVnd(), line.quantity(), line.cartItemId(), sellers));
		}
		return lines;
	}

	private Line verifiedLine(ProductBatchClient.ProductInfo item, UUID quotedSeller, long quotedPrice, int quantity,
			UUID sourceCartItemId, Map<UUID, Seller> sellers) {
		if (!item.sellable()) {
			throw new BuyNowPayRejected(409, "CHECKOUT_ITEMS_INVALID");
		}
		if (!item.sellerId().equals(quotedSeller) || item.priceVnd() != quotedPrice) {
			throw new BuyNowPayRejected(409, "CHECKOUT_QUOTE_CHANGED");
		}
		if (item.coverImageUrl() == null || item.coverImageUrl().isBlank()) {
			throw new BuyNowPayRejected(503, "SERVICE_UNAVAILABLE");
		}
		var seller = sellers.computeIfAbsent(item.sellerId(), sellerId -> {
			if (!identity.sellerIsSellable(sellerId)) {
				throw new BuyNowPayRejected(409, "CHECKOUT_ITEMS_INVALID");
			}
			return identity.seller(sellerId);
		});
		return new Line(item.productId(), seller.sellerId(), seller.sellerName(), item.productName(), item.coverImageUrl(),
				item.priceVnd(), quantity, sourceCartItemId);
	}

	private static boolean uniqueViolation(Throwable failure) {
		for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
			if (cause instanceof SQLException sql && "23505".equals(sql.getSQLState())) {
				return true;
			}
		}
		return false;
	}

	public record PayResult(CheckoutResponse response, boolean replayed) {
	}
}
