package ir.linuxian.second.entities.shopping.order;

public enum OrderStatus {

    PENDING_PAYMENT,   // order created, waiting for the gateway result
    PAID,              // payment confirmed (webhook/verify API)
    PROCESSING,        // being picked/packed
    SHIPPED,           // handed to the carrier
    DELIVERED,         // received by the customer
    CANCELLED,         // cancelled before shipping
    PAYMENT_FAILED,    // gateway declined or the payment timed out
    REFUNDED           // money returned (full refund)
}
