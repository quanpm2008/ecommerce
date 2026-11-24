package com.example.identity_service.internal;

import com.example.identity_service.common.error.ApiException;
import com.example.identity_service.common.error.ErrorCode;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SellerObligationsTests {
    private final UUID seller = UUID.randomUUID();
    private final SellerObligations.Orders orders = mock(SellerObligations.Orders.class);
    private final SellerObligations.Finance finance = mock(SellerObligations.Finance.class);
    private final SellerObligations source = new SellerObligations(orders, finance, "order-token", "finance-token");

    @Test
    void selectsOnlyPermissionsAllowedByCurrentObligations() {
        snapshot(false, false, 0, 9_500);
        assertThat(source.read(seller)).isEqualTo(new SellerObligations.Access("WITHDRAWAL_ONLY",
                Set.of("wallet:read:own", "withdrawal:create:own")));
        snapshot(true, false, 0, 9_500);
        assertThat(source.read(seller)).isEqualTo(new SellerObligations.Access("ORDERS_AND_WITHDRAWAL",
                Set.of("order:fulfill:own", "wallet:read:own", "withdrawal:create:own")));
        snapshot(true, false, 0, 0);
        assertThat(source.read(seller)).isEqualTo(new SellerObligations.Access("OPEN_ORDERS_ONLY",
                Set.of("order:fulfill:own", "wallet:read:own")));
        snapshot(false, true, 0, 0);
        assertThat(source.read(seller)).isEqualTo(new SellerObligations.Access("OBLIGATIONS_READ_ONLY",
                Set.of("wallet:read:own")));
        snapshot(false, false, 0, 0);
        assertThat(source.read(seller).allowed()).isEmpty();
    }

    @Test
    void missingFinanceSnapshotDoesNotGiveWithdrawalPermission() {
        snapshot(false, false, 0, 9_500);
        when(finance.current(seller, "finance-token")).thenReturn(null);
        assertThatThrownBy(() -> source.read(seller)).isInstanceOfSatisfying(ApiException.class,
                error -> assertThat(error.code()).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE));
    }

    private void snapshot(boolean open, boolean pending, long held, long available) {
        Instant now = Instant.now();
        when(orders.current(seller, "order-token"))
                .thenReturn(new SellerObligations.Envelope<>(new SellerObligations.OrderSnapshot(open, now)));
        when(finance.current(seller, "finance-token"))
                .thenReturn(new SellerObligations.Envelope<>(new SellerObligations.FinanceSnapshot(pending, held, available, now)));
    }
}
