package ir.linuxian.second.service;

import ir.linuxian.second.components.ZarinpalClient;
import ir.linuxian.second.config.ZarinpalProperties;
import ir.linuxian.second.entities.User;
import ir.linuxian.second.entities.payment.Payment;
import ir.linuxian.second.entities.payment.PaymentStatus;
import ir.linuxian.second.entities.shopping.order.Order;
import ir.linuxian.second.repos.OrderRepo;
import ir.linuxian.second.repos.PaymentRepo;
import ir.linuxian.second.repos.UserRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class PaymentService {
    private final OrderRepo orders;
    private final PaymentRepo payments;
    private final UserRepo users;
    private final ZarinpalClient zarinpal;
    private final ZarinpalProperties props;

    public PaymentService(OrderRepo orders, PaymentRepo payments, UserRepo users,
                          ZarinpalClient zarinpal, ZarinpalProperties props) {
        this.orders = orders;
        this.payments = payments;
        this.users = users;
        this.zarinpal = zarinpal;
        this.props = props;
    }

    /**
     * Start (or retry) a Zarinpal payment for an order.
     * Guest orders are authorized by the secret accessToken; orders owned by
     * a user additionally require the caller's username to match.
     *
     * @return Map with paymentUrl + authority (frontend redirects to paymentUrl)
     */
    @Transactional
    public Map<String, Object> startPayment(String orderAccessToken, String username) {
        Order order = orders.findByAccessToken(orderAccessToken)
                .orElseThrow(() -> new RuntimeException("Order not found"));
        assertPayable(order, username);

        long amountRial = toRial(order.getTotalAmount());
        if (amountRial <= 0) {
            throw new RuntimeException("Order amount is invalid for online payment");
        }

        String description = "Order " + order.getOrderNumber();
        String email = order.getCustomerEmail();
        String mobile = normalizeMobile(order.getCustomerPhone());

        String authority = zarinpal.requestPayment(amountRial, description, email, mobile);
        payments.save(Payment.start(order, authority));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("paymentUrl", props.startPayUrl() + authority);
        out.put("authority", authority);
        out.put("orderNumber", order.getOrderNumber());
        out.put("accessToken", order.getAccessToken());
        out.put("amount", order.getTotalAmount());
        return out;
    }

    @Transactional
    public Order handleCallback(String authority, String status) {
        Payment payment = payments.findByAuthorityForUpdate(authority)
                .orElseThrow(() -> new RuntimeException("Unknown payment"));
        Order order = payment.getOrder();

        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            return order; // idempotent: Zarinpal may call back twice
        }

        if (!"OK".equalsIgnoreCase(status)) {
            // User cancelled on the gateway: keep order payable for a retry.
            payment.markFailed();
            return order;
        }

        var result = zarinpal.verify(authority, toRial(payment.getAmount()));
        if (result.success()) {
            payment.markSuccess(result.refId(), result.cardPan());
            order.markPaid(String.valueOf(result.refId()));
        } else {
            payment.markFailed();
            order.markPaymentFailed();
        }
        return order;
    }

    @Transactional(readOnly = true)
    public String findAccessTokenByAuthority(String authority) {
        return payments.findByAuthority(authority)
                .orElseThrow(() -> new RuntimeException("Unknown payment"))
                .getOrder().getAccessToken();
    }

    private void assertPayable(Order order, String username) {
        if (!order.isPayable()) {
            throw new IllegalStateException("Order cannot be paid (status=" + order.getStatus() + ")");
        }
        User owner = order.getUser();
        if (owner != null && owner.getUsername() != null && username != null && !username.isBlank()) {
            if (!owner.getUsername().equals(username)) {
                throw new RuntimeException("Order does not belong to this user");
            }
        }
        // Guest order (owner == null) or anonymous caller with token: allowed via secret token.
    }

    /** Zarinpal mobile must be digits (IR numbers like 0912...). Best-effort cleanup. */
    private String normalizeMobile(String phone) {
        if (phone == null) return null;
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.startsWith("98") && digits.length() > 10) {
            digits = "0" + digits.substring(2);
        }
        return digits.isEmpty() ? null : digits;
    }

    /** Prices are stored in Rial (IRR). Zarinpal v4 expects integer Rial. */
    private long toRial(BigDecimal amount) {
        if (amount == null) {
            throw new RuntimeException("Order amount is missing");
        }
        return amount.setScale(0, RoundingMode.HALF_UP).longValueExact();
    }
}
