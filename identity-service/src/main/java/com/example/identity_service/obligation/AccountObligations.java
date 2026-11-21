package com.example.identity_service.obligation;

import com.example.identity_service.user.AccessMode;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.example.identity_service.user.AccountType;

import java.time.Instant;

/**
 * An account's current obligations (API §3.2 G-14, §6.1 {@code BanImpact.obligations}, §7.1.2), from Order and, for a
 * seller, Finance. A customer has {@code hasPendingFinance = false} and zero balances. Never a historical snapshot:
 * having paid once does not count, only what is still open.
 */
public record AccountObligations(boolean hasOpenOrders, boolean hasPendingPayment, boolean hasUnresolvedRefund,
		boolean hasPendingFinance, long pendingBalanceVnd, long availableBalanceVnd, @JsonIgnore Instant evaluatedAt) {

	/** Whether anything still keeps the account BANNED (with a limited session) rather than DISABLED. */
	public boolean any() {
		return hasOpenOrders || hasPendingPayment || hasUnresolvedRefund || hasPendingFinance || pendingBalanceVnd > 0
				|| availableBalanceVnd > 0;
	}

	/**
	 * What a banned account may still do (API §3.2 table):
	 * <ul>
	 * <li>Customer: open orders, or a refund it may retry → {@code OPEN_ORDERS_ONLY}; only a payment the system is
	 * settling → {@code OBLIGATIONS_READ_ONLY}.</li>
	 * <li>Seller: open orders and available money → {@code ORDERS_AND_WITHDRAWAL}; only open orders →
	 * {@code OPEN_ORDERS_ONLY}; only available money → {@code WITHDRAWAL_ONLY}; only pending payments, refunds,
	 * settlements or a pending balance → {@code OBLIGATIONS_READ_ONLY}.</li>
	 * <li>Nothing left → {@code LOGIN_BLOCKED} (the account becomes DISABLED).</li>
	 * </ul>
	 */
	public AccessMode accessMode(AccountType accountType) {
		if (!any()) {
			return AccessMode.LOGIN_BLOCKED;
		}
		if (accountType == AccountType.SELLER) {
			boolean available = availableBalanceVnd > 0;
			if (hasOpenOrders && available) {
				return AccessMode.ORDERS_AND_WITHDRAWAL;
			}
			if (hasOpenOrders) {
				return AccessMode.OPEN_ORDERS_ONLY;
			}
			return available ? AccessMode.WITHDRAWAL_ONLY : AccessMode.OBLIGATIONS_READ_ONLY;
		}
		return hasOpenOrders || hasUnresolvedRefund ? AccessMode.OPEN_ORDERS_ONLY : AccessMode.OBLIGATIONS_READ_ONLY;
	}

}
