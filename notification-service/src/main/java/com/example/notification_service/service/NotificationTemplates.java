package com.example.notification_service.service;

import com.example.notification_service.common.error.ApiException;
import com.example.notification_service.common.error.ErrorCode;
import com.example.notification_service.model.NotificationData.CreateRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Allowlisted plain-text templates; producers cannot supply HTML, redirects or message bodies. */
@Component
public class NotificationTemplates {
    private static final String SIMULATED = " Giả lập — không phát sinh tiền thật.";
    private static final Map<String, Set<String>> PRODUCERS = Map.of(
            "identity-service", Set.of("SELLER_REGISTERED", "SELLER_APPROVED", "SELLER_REJECTED",
                    "ACCOUNT_BANNED", "ACCOUNT_UNBANNED", "USER_DISABLED", "USER_ENABLED"),
            "product-service", Set.of("PRODUCT_REMOVED"),
            "order-service", Set.of("CHECKOUT_PAID", "NEW_ORDER", "PAYMENT_FAILED", "CHECKOUT_EXPIRED",
                    "ORDER_DELIVERED", "ORDER_REJECTED", "ORDER_CANCELLED", "ORDER_AUTO_CANCELLED",
                    "ORDER_CONFIRMED", "ORDER_AUTO_CONFIRMED"),
            "payment-service", Set.of("REFUND_SUCCEEDED", "REFUND_RETRY_REQUIRED"),
            "finance-service", Set.of("FUNDS_AVAILABLE", "WITHDRAWAL_COMPLETED"));

    public record Message(String title, String content) {}

    public Message render(String producer, CreateRequest request) {
        if (request == null || request.type() == null ||
                !PRODUCERS.getOrDefault(producer, Set.of()).contains(request.type())) {
            throw new ApiException(ErrorCode.NOTIFICATION_TYPE_NOT_ALLOWED);
        }
        Map<String, Object> data = request.templateData();
        require(data != null, "templateData");
        String expectedAudience = switch (request.type()) {
            case "NEW_ORDER", "FUNDS_AVAILABLE", "WITHDRAWAL_COMPLETED", "SELLER_REGISTERED",
                    "SELLER_APPROVED", "SELLER_REJECTED", "PRODUCT_REMOVED" -> "SELLER";
            case "ACCOUNT_BANNED", "ACCOUNT_UNBANNED", "USER_DISABLED", "USER_ENABLED" -> "ACCOUNT";
            case "ORDER_CANCELLED", "ORDER_REJECTED", "ORDER_AUTO_CANCELLED" -> request.audienceContext();
            default -> "CUSTOMER";
        };
        require(Set.of("CUSTOMER", "SELLER", "ACCOUNT").contains(request.audienceContext()), "audienceContext");
        require(expectedAudience.equals(request.audienceContext()), "audienceContext");
        if (Set.of("ORDER_CANCELLED", "ORDER_REJECTED", "ORDER_AUTO_CANCELLED").contains(request.type())) {
            require(!"ACCOUNT".equals(request.audienceContext()), "audienceContext");
        }
        Set<String> allowed = switch (request.type()) {
            case "CHECKOUT_PAID" -> Set.of("checkoutSessionId", "totalAmountVnd", "orders");
            case "NEW_ORDER" -> Set.of("orderId", "sellerName", "totalAmountVnd");
            case "PAYMENT_FAILED", "CHECKOUT_EXPIRED" -> Set.of("checkoutSessionId");
            case "ORDER_DELIVERED", "ORDER_CONFIRMED", "ORDER_AUTO_CONFIRMED" -> Set.of("orderId", "sellerName");
            case "ORDER_CANCELLED", "ORDER_REJECTED", "ORDER_AUTO_CANCELLED" -> Set.of("orderId", "sellerName", "reasonCode", "reasonNote");
            case "REFUND_SUCCEEDED", "REFUND_RETRY_REQUIRED" -> Set.of("refundId", "paymentId", "checkoutSessionId", "orderId", "scope", "amountVnd", "retryCycle");
            case "FUNDS_AVAILABLE" -> Set.of("orderId", "sellerAmountVnd");
            case "WITHDRAWAL_COMPLETED" -> Set.of("withdrawalId", "withdrawalCode", "amountVnd", "availableAfterVnd");
            case "SELLER_REGISTERED", "SELLER_APPROVED", "SELLER_REJECTED" -> Set.of("reason");
            case "PRODUCT_REMOVED" -> Set.of("productName", "reason");
            default -> Set.of("accountStatus", "accessMode", "restoreUntil", "reason");
        };
        require(allowed.containsAll(data.keySet()), "templateData");
        if (request.reference() != null) {
            require(request.reference().id() != null && request.reference().type() != null, "reference");
            String referenceType = switch (request.type()) {
                case "CHECKOUT_PAID", "PAYMENT_FAILED", "CHECKOUT_EXPIRED" -> "CHECKOUT_SESSION";
                case "REFUND_SUCCEEDED", "REFUND_RETRY_REQUIRED" -> "REFUND";
                case "WITHDRAWAL_COMPLETED" -> "WITHDRAWAL";
                case "PRODUCT_REMOVED" -> "PRODUCT";
                case "SELLER_REGISTERED", "SELLER_APPROVED", "SELLER_REJECTED" -> "SELLER";
                case "ACCOUNT_BANNED", "ACCOUNT_UNBANNED", "USER_DISABLED", "USER_ENABLED" -> "USER";
                default -> "ORDER";
            };
            require(referenceType.equals(request.reference().type()), "reference.type");
            require("WITHDRAWAL".equals(referenceType) || request.reference().code() == null, "reference.code");
        }
        return switch (request.type()) {
            case "CHECKOUT_PAID" -> new Message("Thanh toán thành công", checkout(data));
            case "NEW_ORDER" -> new Message("Đơn hàng mới", order(data) + ", tổng tiền " + money(data, "totalAmountVnd") + "đ.");
            case "PAYMENT_FAILED" -> new Message("Thanh toán thất bại", "Phiên thanh toán " + uuid(data, "checkoutSessionId") + " đã đóng do thanh toán thất bại. Không xác nhận đã hoàn tiền.");
            case "CHECKOUT_EXPIRED" -> new Message("Phiên thanh toán hết hạn", "Phiên thanh toán " + uuid(data, "checkoutSessionId") + " đã hết hạn.");
            case "ORDER_DELIVERED" -> new Message("Đơn hàng đã giao", order(data) + " đã được xác nhận giao. Hãy kiểm tra và xác nhận đã nhận hàng.");
            case "ORDER_CONFIRMED", "ORDER_AUTO_CONFIRMED" -> new Message("Đã xác nhận nhận hàng", order(data) + ("ORDER_AUTO_CONFIRMED".equals(request.type()) ? " đã được hệ thống tự xác nhận nhận hàng." : " đã được xác nhận nhận hàng."));
            case "ORDER_REJECTED", "ORDER_CANCELLED", "ORDER_AUTO_CANCELLED" -> new Message(
                    "ORDER_REJECTED".equals(request.type()) ? "Người bán từ chối giao hàng" : "Đơn hàng đã hủy",
                    order(data) + " đã hủy." + optional(data, "reasonCode") + optional(data, "reasonNote") + " Chỉ xác nhận hoàn tiền khi có kết quả hoàn thành công.");
            case "REFUND_SUCCEEDED" -> new Message("Hoàn tiền thành công", refund(data) + " đã hoàn thành." + SIMULATED);
            case "REFUND_RETRY_REQUIRED" -> {
                require(request.reference() != null, "reference");
                long cycle = positive(data, "retryCycle");
                yield new Message("Cần thử lại hoàn tiền", refund(data) + " đã hết lượt tự động ở chu kỳ " + cycle + ". Mở mục Thử lại hoàn tiền, đăng nhập và xác nhận để bắt đầu chu kỳ mới cho cùng khoản hoàn; liên kết không tự thực hiện hoàn tiền." + SIMULATED);
            }
            case "FUNDS_AVAILABLE" -> new Message("Tiền đã chuyển sang khả dụng", "Đơn " + uuid(data, "orderId") + ": " + money(data, "sellerAmountVnd") + "đ vừa chuyển sang số dư khả dụng." + SIMULATED);
            case "WITHDRAWAL_COMPLETED" -> new Message("Rút tiền thành công", "Biên nhận " + text(data, "withdrawalCode") + " (" + uuid(data, "withdrawalId") + "): " + money(data, "amountVnd") + "đ; số dư khả dụng còn " + money(data, "availableAfterVnd") + "đ." + SIMULATED);
            case "SELLER_REGISTERED" -> new Message("Đã nhận hồ sơ người bán", "Hồ sơ đang chờ xét duyệt; tài khoản chưa được đăng nhập.");
            case "SELLER_APPROVED" -> new Message("Hồ sơ người bán được duyệt", "Bạn có thể đăng nhập tài khoản người bán.");
            case "SELLER_REJECTED" -> new Message("Hồ sơ người bán bị từ chối", "Bạn cần xác minh email và nộp hồ sơ mới nếu muốn đăng ký lại." + optional(data, "reason"));
            case "PRODUCT_REMOVED" -> new Message("Sản phẩm đã bị gỡ", "Sản phẩm " + text(data, "productName") + " đã bị gỡ." + optional(data, "reason"));
            default -> account(request.type(), data);
        };
    }

    private static Message account(String type, Map<String, Object> data) {
        String status = text(data, "accountStatus");
        String access = text(data, "accessMode");
        String suffix = optional(data, "reason");
        return switch (type) {
            case "ACCOUNT_UNBANNED" -> {
                require("ACTIVE".equals(status), "accountStatus");
                yield new Message("Tài khoản đã được gỡ cấm", "Tài khoản đã được khôi phục đầy đủ." + suffix);
            }
            case "USER_DISABLED" -> {
                require("DISABLED".equals(status), "accountStatus");
                Object until = data.get("restoreUntil");
                String deadline = "";
                if (until != null) {
                    try { deadline = " Hạn khôi phục: " + Instant.parse(until.toString()) + "."; }
                    catch (RuntimeException ex) { throw invalid("restoreUntil"); }
                }
                yield new Message("Tài khoản đã vô hiệu hóa", "Tài khoản đã vô hiệu hóa sau khi xử lý nghĩa vụ." + deadline + suffix);
            }
            case "USER_ENABLED" -> {
                if ("BANNED".equals(status)) {
                    yield new Message("Mở lại quyền xử lý nghĩa vụ", "Tài khoản vẫn bị cấm; chỉ được xử lý nghĩa vụ hiện tại (" + access + "), không khôi phục quyền mua/bán." + suffix);
                }
                require("ACTIVE".equals(status), "accountStatus");
                yield new Message("Tài khoản đang hoạt động", "Tài khoản đã hoạt động trở lại." + suffix);
            }
            default -> {
                require("BANNED".equals(status) || "DISABLED".equals(status), "accountStatus");
                yield new Message("Tài khoản bị cấm", "Tài khoản bị cấm; chế độ truy cập: " + access + "." + suffix);
            }
        };
    }

    private static String checkout(Map<String, Object> data) {
        UUID checkout = uuid(data, "checkoutSessionId");
        long total = money(data, "totalAmountVnd");
        require(data.get("orders") instanceof List<?> list && !list.isEmpty(), "orders");
        List<?> orders = (List<?>) data.get("orders");
        StringBuilder content = new StringBuilder("Phiên thanh toán ").append(checkout).append(": ").append(total).append("đ. Các đơn: ");
        long sum = 0;
        for (Object value : orders) {
            require(value instanceof Map<?, ?>, "orders");
            @SuppressWarnings("unchecked") Map<String, Object> row = (Map<String, Object>) value;
            require(Set.of("orderId", "sellerName", "totalAmountVnd").containsAll(row.keySet()), "orders");
            long amount = money(row, "totalAmountVnd");
            try { sum = Math.addExact(sum, amount); }
            catch (ArithmeticException ex) { throw invalid("totalAmountVnd"); }
            content.append(order(row)).append(" ").append(amount).append("đ; ");
        }
        require(sum == total, "totalAmountVnd");
        return content.toString();
    }

    private static String order(Map<String, Object> data) {
        return "Đơn " + uuid(data, "orderId") + " tại " + text(data, "sellerName");
    }

    private static String refund(Map<String, Object> data) {
        UUID refundId = uuid(data, "refundId");
        uuid(data, "paymentId");
        UUID checkout = uuid(data, "checkoutSessionId");
        String scope = text(data, "scope");
        require(Set.of("ORDER", "FULL_CHECKOUT").contains(scope), "scope");
        String target = "ORDER".equals(scope) ? "đơn " + uuid(data, "orderId") : "toàn bộ phiên " + checkout;
        return "Khoản hoàn " + refundId + " cho " + target + ", số tiền " + money(data, "amountVnd") + "đ";
    }

    static UUID uuid(Map<String, Object> data, String name) {
        try { return UUID.fromString(text(data, name)); }
        catch (IllegalArgumentException ex) { throw invalid(name); }
    }

    static String text(Map<String, Object> data, String name) {
        Object value = data.get(name);
        require(value instanceof String string && !string.isBlank(), name);
        return ((String) value).strip();
    }

    static long money(Map<String, Object> data, String name) {
        Object value = data.get(name);
        require(value instanceof Number, name);
        try {
            long amount = new BigDecimal(value.toString()).longValueExact();
            require(amount >= 0, name);
            return amount;
        }
        catch (ArithmeticException | NumberFormatException ex) { throw invalid(name); }
    }

    static long positive(Map<String, Object> data, String name) {
        long value = money(data, name);
        require(value > 0, name);
        return value;
    }

    private static String optional(Map<String, Object> data, String name) {
        return data.get(name) == null ? "" : " " + text(data, name);
    }

    static void require(boolean condition, String field) {
        if (!condition) { throw invalid(field); }
    }

    private static ApiException invalid(String field) {
        return ApiException.forField(ErrorCode.VALIDATION_ERROR, field, "INVALID_TEMPLATE_DATA");
    }
}
