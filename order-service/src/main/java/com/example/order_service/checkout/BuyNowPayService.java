package com.example.order_service.checkout;

import com.example.order_service.client.CheckoutIdentityClient;
import com.example.order_service.client.FinanceCommissionClient;
import com.example.order_service.client.PayIdentityClient;
import com.example.order_service.client.ProductBatchClient;
import com.example.order_service.security.OrderAuthorization.CurrentUser;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
class BuyNowPayService {
	private final CheckoutQuote quote;
	private final CheckoutIdentityClient identity;
	private final PayIdentityClient addresses;
	private final ProductBatchClient products;
	private final FinanceCommissionClient finance;
	private final CheckoutCommitRepository commit;
	private final PendingCheckoutReader pending;
	private final CheckoutReplayRepository replays;

	BuyNowPayService(CheckoutQuote quote, CheckoutIdentityClient identity, PayIdentityClient addresses,
			ProductBatchClient products, FinanceCommissionClient finance, CheckoutCommitRepository commit,
			PendingCheckoutReader pending, CheckoutReplayRepository replays) {
		this.quote = quote;
		this.identity = identity;
		this.addresses = addresses;
		this.products = products;
		this.finance = finance;
		this.commit = commit;
		this.pending = pending;
		this.replays = replays;
	}

	PayResult pay(CurrentUser customer, String key, String quoteToken, UUID addressId) {
		var claims = quote.verify(quoteToken, customer.userId());
		String hash = quote.requestHash(claims, addressId);
		CheckoutResponse replay = replays.findOrRegisterAlias(customer.userId(), key, claims.checkoutIntentId(), hash);
		if (replay != null) {
			return new PayResult(replay, true);
		}
		try {
			return new PayResult(freshPay(customer, key, claims, addressId, hash), false);
		} catch (DuplicateKeyException | Rejected failure) {
			// A concurrent Pay can commit after the first lookup and before an eligibility or uniqueness check.
			replay = replays.findOrRegisterAlias(customer.userId(), key, claims.checkoutIntentId(), hash);
			if (replay != null) {
				return new PayResult(replay, true);
			}
			if (failure instanceof DuplicateKeyException && pending.pendingSession(customer.userId()).isPresent()) {
				throw new Rejected(409, "CHECKOUT_PENDING_EXISTS");
			}
			throw failure;
		}
	}

	private CheckoutResponse freshPay(CurrentUser customer, String key, CheckoutQuote.Claims claims, UUID addressId,
			String hash) {
		if (quote.isExpired(claims)) {
			throw new Rejected(409, "CHECKOUT_QUOTE_EXPIRED");
		}
		pending.pendingSession(customer.userId()).ifPresent(id -> {
			throw new Rejected(409, "CHECKOUT_PENDING_EXISTS");
		});
		var eligibility = identity.eligibility(customer.userId());
		if (eligibility.authzVersion() != customer.authzVersion()) {
			throw new Rejected(401, "TOKEN_STALE");
		}
		if (eligibility.paymentBlocked() || !"ACTIVE".equals(eligibility.customerStatus())
				|| !"ACTIVE".equals(eligibility.userStatus())) {
			throw new Rejected(403, "CUSTOMER_PAYMENT_BLOCKED");
		}
		if (!eligibility.hasPhone() || eligibility.addressCount() == 0) {
			throw new Rejected(422, "PROFILE_INCOMPLETE");
		}
		if (!eligibility.canCheckout()) {
			throw new Rejected(503, "SERVICE_UNAVAILABLE");
		}
		var shippingAddress = addresses.address(customer.userId(), addressId);
		var results = products.getProducts(List.of(claims.productId()));
		if (results.isEmpty() || !results.getFirst().sellable()) {
			throw new Rejected(409, "CHECKOUT_ITEMS_INVALID");
		}
		var item = results.getFirst();
		if (!item.sellerId().equals(claims.sellerId()) || item.priceVnd() != claims.unitPriceVnd()) {
			throw new Rejected(409, "CHECKOUT_QUOTE_CHANGED");
		}
		if (!identity.sellerIsSellable(item.sellerId())) {
			throw new Rejected(409, "CHECKOUT_ITEMS_INVALID");
		}
		if (item.coverImageUrl() == null || item.coverImageUrl().isBlank()) {
			throw new Rejected(503, "SERVICE_UNAVAILABLE");
		}
		var seller = identity.seller(item.sellerId());
		int commissionBps = finance.commissionBps();
		var line = new CheckoutCommitRepository.Line(item.productId(), seller.sellerId(), seller.sellerName(),
				item.productName(), item.coverImageUrl(), item.priceVnd(), claims.quantity());
		if (quote.isExpired(claims)) {
			throw new Rejected(409, "CHECKOUT_QUOTE_EXPIRED");
		}
		CheckoutResponse response;
		try {
			response = commit.commit(new CheckoutCommitRepository.NewCheckout(customer.userId(), claims.checkoutIntentId(),
					key, hash, addressId, shippingAddress, commissionBps, List.of(line)));
		} catch (CheckoutCommitRepository.StockUnavailable unavailable) {
			throw new Rejected(409, "CHECKOUT_ITEMS_INVALID");
		}
		addresses.touch(customer.userId(), addressId, response.checkoutSessionId());
		return response;
	}

	record PayResult(CheckoutResponse response, boolean replayed) {
	}

	static final class Rejected extends RuntimeException {
		final int status;
		final String code;

		Rejected(int status, String code) {
			this.status = status;
			this.code = code;
		}
	}
}
