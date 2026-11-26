package com.example.simulator;

import java.net.URI;
import java.text.NumberFormat;
import java.util.Locale;

import com.example.simulator.SimulatorModels.GatewayStatus;
import com.example.simulator.SimulatorModels.PaymentRecord;

public final class PaymentPageRenderer {
    private PaymentPageRenderer() {
    }

    public static String renderPaymentPage(PaymentRecord payment, String notice, URI frontendReturnBaseUrl) {
        String amountFormatted = NumberFormat.getIntegerInstance(Locale.US).format(payment.amountVnd) + " VND";
        StringBuilder actions = new StringBuilder();
        if (payment.status == GatewayStatus.PENDING) {
            actions.append("""
                    <form method="post" action="/pay/%s/confirm" class="actions">
                        <button type="submit" name="action" value="SUCCESS" class="btn btn-success">
                            Xác nhận thanh toán (SUCCESS)
                        </button>
                        <button type="submit" name="action" value="FAILED" class="btn btn-fail">
                            Từ chối thanh toán (FAILED)
                        </button>
                    </form>
                    """.formatted(escape(payment.token)));
        } else {
            String badgeClass = payment.status == GatewayStatus.SUCCESS ? "status-success" : "status-failed";
            String returnLinkHtml = "";
            if (frontendReturnBaseUrl != null && payment.checkoutSessionId != null) {
                String base = frontendReturnBaseUrl.toString();
                String target = base.endsWith("/") ? base + payment.checkoutSessionId : base + "/" + payment.checkoutSessionId;
                returnLinkHtml = """
                        <div class="return-wrap">
                            <a href="%s" class="btn btn-return">Quay về trang chi tiết phiên</a>
                        </div>
                        """.formatted(escape(target));
            }
            actions.append("""
                    <div class="result-box %s">
                        <strong>Trạng thái kết thúc: %s</strong>
                        <p>Mã giao dịch provider: <code>%s</code></p>
                        <p>Thời điểm hoàn tất: <code>%s</code></p>
                        %s
                    </div>
                    %s
                    """.formatted(
                    badgeClass,
                    escape(payment.status.name()),
                    escape(payment.providerTransactionId),
                    escape(String.valueOf(payment.completedAt)),
                    payment.errorCode != null ? "<p>Mã lỗi: <code>" + escape(payment.errorCode) + "</code></p>" : "",
                    returnLinkHtml
            ));
        }

        String noticeHtml = notice == null || notice.isBlank()
                ? ""
                : "<div class=\"notice\">" + escape(notice) + "</div>";

        return """
                <!DOCTYPE html>
                <html lang="vi">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>Cổng thanh toán giả lập</title>
                    <style>
                        body { font-family: system-ui, -apple-system, sans-serif; background: #f4f6f8; color: #1f2937; margin: 0; padding: 24px; }
                        .card { max-width: 540px; margin: 32px auto; background: #fff; border-radius: 12px; padding: 28px; box-shadow: 0 8px 24px rgba(0,0,0,0.08); }
                        .badge { display: inline-block; background: #fef3c7; color: #92400e; padding: 4px 10px; border-radius: 999px; font-size: 12px; font-weight: 600; margin-bottom: 12px; }
                        h1 { font-size: 22px; margin: 0 0 16px; }
                        .amount { font-size: 28px; font-weight: 700; color: #111827; margin: 12px 0 20px; }
                        .meta { background: #f9fafb; border: 1px solid #e5e7eb; border-radius: 8px; padding: 14px; font-size: 14px; line-height: 1.7; }
                        .meta code { font-family: ui-monospace, SFMono-Regular, monospace; font-size: 12px; color: #374151; }
                        .actions { display: flex; gap: 12px; margin-top: 22px; flex-wrap: wrap; }
                        .btn { flex: 1; min-width: 180px; border: none; border-radius: 8px; padding: 12px 16px; font-size: 15px; font-weight: 600; cursor: pointer; }
                        .btn-success { background: #16a34a; color: #fff; }
                        .btn-fail { background: #dc2626; color: #fff; }
                        .btn-return { display: inline-block; text-decoration: none; text-align: center; background: #1d4ed8; color: #fff; }
                        .return-wrap { margin-top: 16px; }
                        .result-box { margin-top: 20px; padding: 16px; border-radius: 8px; font-size: 14px; }
                        .status-success { background: #dcfce7; border: 1px solid #86efac; color: #14532d; }
                        .status-failed { background: #fee2e2; border: 1px solid #fca5a5; color: #7f1d1d; }
                        .notice { margin-top: 14px; padding: 10px 14px; border-radius: 8px; background: #eff6ff; color: #1e40af; font-size: 14px; }
                    </style>
                </head>
                <body>
                    <div class="card">
                        <div class="badge">Giả lập — không phát sinh tiền thật</div>
                        <h1>Xác nhận thanh toán</h1>
                        <div class="amount">%s</div>
                        <div class="meta">
                            <div><strong>Payment ID:</strong> <code>%s</code></div>
                            <div><strong>Checkout Session:</strong> <code>%s</code></div>
                            <div><strong>Trạng thái:</strong> <code>%s</code></div>
                            <div><strong>Hết hạn lúc:</strong> <code>%s</code></div>
                        </div>
                        %s
                        %s
                    </div>
                </body>
                </html>
                """.formatted(
                escape(amountFormatted),
                escape(String.valueOf(payment.paymentId)),
                escape(String.valueOf(payment.checkoutSessionId)),
                escape(payment.status.name()),
                escape(String.valueOf(payment.expiresAt)),
                noticeHtml,
                actions.toString()
        );
    }

    public static String renderNotFound() {
        return """
                <!DOCTYPE html>
                <html lang="vi">
                <head><meta charset="UTF-8"><title>Không tìm thấy giao dịch</title></head>
                <body style="font-family: system-ui, sans-serif; padding: 40px; text-align: center;">
                    <h2>Không tìm thấy phiên thanh toán giả lập</h2>
                    <p>Liên kết thanh toán không tồn tại hoặc đã bị xóa.</p>
                </body>
                </html>
                """;
    }

    private static String escape(String input) {
        if (input == null) {
            return "";
        }
        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
