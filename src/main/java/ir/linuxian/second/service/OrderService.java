package ir.linuxian.second.service;

import ir.linuxian.second.entities.User;
import ir.linuxian.second.entities.product.Product;
import ir.linuxian.second.entities.shopping.basket.Basket;
import ir.linuxian.second.entities.shopping.basket.BasketItem;
import ir.linuxian.second.entities.shopping.basket.BasketStatus;
import ir.linuxian.second.entities.shopping.order.Order;
import ir.linuxian.second.entities.shopping.order.OrderItem;
import ir.linuxian.second.entities.shopping.order.OrderStatus;
import ir.linuxian.second.repos.BasketItemRepo;
import ir.linuxian.second.repos.BasketRepo;
import ir.linuxian.second.repos.OrderItemRepo;
import ir.linuxian.second.repos.OrderRepo;
import ir.linuxian.second.repos.ProductRepo;
import ir.linuxian.second.repos.UserRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Order-only logic: checkout (basket -> order), user order history,
 * guest tracking by accessToken, and admin status updates.
 * Never edits basket items directly except marking the source basket
 * CHECKED_OUT after a successful snapshot.
 */
@Service
public class OrderService {

    private final OrderRepo orderRepo;
    private final OrderItemRepo orderItemRepo;
    private final BasketRepo basketRepo;
    private final BasketItemRepo basketItemRepo;
    private final ProductRepo productRepo;
    private final UserRepo userRepo;
    private final NotificationService notificationService;
    private final SettingService settingService;

    public OrderService(OrderRepo orderRepo, OrderItemRepo orderItemRepo,
                        BasketRepo basketRepo, BasketItemRepo basketItemRepo,
                        ProductRepo productRepo, UserRepo userRepo,
                        NotificationService notificationService,
                        SettingService settingService) {
        this.orderRepo = orderRepo;
        this.orderItemRepo = orderItemRepo;
        this.basketRepo = basketRepo;
        this.basketItemRepo = basketItemRepo;
        this.productRepo = productRepo;
        this.userRepo = userRepo;
        this.notificationService = notificationService;
        this.settingService = settingService;
    }

    /**
     * Create an Order snapshot from an OPEN basket.
     *
     * @param basketToken basket token (X-Basket-Token header)
     * @param username    logged-in username or null for guest
     */
    @Transactional
    public Map<String, Object> checkout(String basketToken, String username,
                                        String name, String phone,
                                        String address, String email) {
        if (basketToken == null || basketToken.isBlank()) {
            throw new RuntimeException("Basket token is required");
        }
        Basket basket = basketRepo.findByToken(basketToken)
                .orElseThrow(() -> new RuntimeException("Basket not found"));

        if (basket.getStatus() != BasketStatus.OPEN) {
            throw new RuntimeException("Basket is already ordered");
        }

        // Attach user if logged in and basket is anonymous.
        User user = null;
        if (username != null && !username.isBlank()) {
            user = userRepo.findByUsername(username).orElse(null);
            if (user != null && basket.getUser() == null) {
                basket.setUser(user);
                basketRepo.save(basket);
            } else if (user != null) {
                // basket already linked; keep it
            }
            if (basket.getUser() == null) {
                basket.setUser(user);
                basketRepo.save(basket);
            }
            if (user == null) {
                throw new RuntimeException("User not found");
            }
        }

        List<BasketItem> items = basketItemRepo.findByBasket(basket);
        if (items.isEmpty()) {
            throw new RuntimeException("Basket is empty");
        }

        String finalName = name == null ? "" : name.trim();
        String finalPhone = phone == null ? "" : phone.trim();
        String finalAddress = address == null ? "" : address.trim();
        String finalEmail = email == null ? "" : email.trim();

        boolean addressNeeded = items.stream()
                .map(BasketItem::getProduct)
                .anyMatch(p -> "PAPER".equals(ProductService.normalizeBookType(p == null ? null : p.getBookType())));
        if (addressNeeded && finalAddress.isEmpty()) {
            throw new RuntimeException("Address is required for paper books");
        }

        if (user != null) {
            if (finalName.isEmpty() || finalPhone.isEmpty() || finalEmail.isEmpty()) {
                if (finalName.isEmpty()) {
                    String full = ((user.getFirstName() == null ? "" : user.getFirstName() + " ")
                            + (user.getLastName() == null ? "" : user.getLastName())).trim();
                    if (!full.isEmpty()) finalName = full;
                }
                if (finalPhone.isEmpty() && user.getPhone() != null) {
                    finalPhone = user.getPhone();
                }
                if (finalEmail.isEmpty() && user.getEmail() != null) {
                    finalEmail = user.getEmail();
                }
            }
        } else if (finalName.isEmpty() || finalPhone.isEmpty()) {
            throw new RuntimeException("Name and phone are required");
        }

        // Validate stock + per-order limits + compute total with effective (discounted) prices.
        BigDecimal total = BigDecimal.ZERO;
        for (BasketItem item : items) {
            Product product = item.getProduct();
            if (!Boolean.TRUE.equals(product.getActive())) {
                throw new RuntimeException(product.getName() + " is no longer available");
            }
            if (product.getStock() == null || product.getStock() < item.getQty()) {
                throw new RuntimeException("Only " + product.getStock() + " left of " + product.getName());
            }
            int limit = ProductService.effectiveMaxPerOrder(product);
            if (item.getQty() > limit) {
                if (ProductService.isEbook(product)) {
                    throw new RuntimeException(product.getName() + " is an e-book and is limited to 1 per order");
                }
                throw new RuntimeException(product.getName() + " is limited to " + limit + " per order");
            }
            total = total.add(ProductService.effectivePrice(product)
                    .multiply(BigDecimal.valueOf(item.getQty())));
        }

        // Decrement stock.
        for (BasketItem item : items) {
            Product product = item.getProduct();
            product.setStock(product.getStock() - item.getQty());
            productRepo.save(product);
        }

        // Create order + snapshot items.
        Order order = new Order();
        order.setAccessToken(UUID.randomUUID().toString());
        order.setUser(user);
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setTotalAmount(total);
        order.setCustomerName(finalName);
        order.setCustomerPhone(finalPhone);
        order.setCustomerAddress(finalAddress);
        order.setCustomerEmail(finalEmail.isEmpty() ? null : finalEmail);
        order.setOrderedAt(Instant.now());
        // orderNumber needs the id; save first with a temp value.
        order.setOrderNumber("TMP-" + UUID.randomUUID());
        order = orderRepo.save(order);

        String orderNumber = "ORD-" + order.getId() + "-"
                + Long.toString(Instant.now().toEpochMilli(), 36).toUpperCase();
        order.setOrderNumber(orderNumber);

        for (BasketItem item : items) {
            Product product = item.getProduct();
            BigDecimal unit = ProductService.effectivePrice(product);
            OrderItem snapshot = OrderItem.snapshotWithPrice(order, product, unit, item.getQty());
            order.getItems().add(snapshot);
        }
        order = orderRepo.save(order);

        // Freeze the basket so the next basket request creates a fresh one.
        basket.setStatus(BasketStatus.CHECKED_OUT);
        basketRepo.save(basket);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", order.getId());
        result.put("orderNumber", orderNumber);
        result.put("accessToken", order.getAccessToken());
        result.put("status", order.getStatus().name());
        result.put("total", total);
        result.put("itemCount", items.size());
        result.put("orderedAt", order.getOrderedAt());
        if (user != null) {
            notificationService.notify(username, "ORDER_PLACED",
                    orderNumber, formatTotal(total), "/orders");
        }
        return result;
    }

    public List<Map<String, Object>> listForUser(String username) {
        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return orderRepo.findByUserOrderByOrderedAtDesc(user)
                .stream().map(this::toSummary).toList();
    }

    public Map<String, Object> getForUser(Long id, String username) {
        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Order order = orderRepo.findByIdAndUser(id, user)
                .orElseThrow(() -> new RuntimeException("Order not found: " + id));
        return toDetail(order);
    }

    /** Guest tracking (no login): secret accessToken from checkout. */
    public Map<String, Object> getByAccessToken(String accessToken) {
        Order order = orderRepo.findByAccessToken(accessToken)
                .orElseThrow(() -> new RuntimeException("Order not found"));
        return toDetail(order);
    }

    public List<Map<String, Object>> listAll(String status) {
        if (status != null && !status.isBlank()) {
            OrderStatus s;
            try {
                s = OrderStatus.valueOf(status.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new RuntimeException("Invalid status: " + status);
            }
            return orderRepo.findByStatusOrderByOrderedAtDesc(s)
                    .stream().map(this::toSummary).toList();
        }
        return orderRepo.findAllByOrderByOrderedAtDesc().stream()
                .map(this::toSummary).toList();
    }

    public Map<String, Object> getOrThrow(Long id) {
        Order order = orderRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found: " + id));
        return toDetail(order);
    }

    @Transactional
    public Map<String, Object> setStatus(Long id, String status) {
        Order order = orderRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found: " + id));
        String s = status == null ? "" : status.trim().toUpperCase();
        OrderStatus next;
        try {
            next = OrderStatus.valueOf(s);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Invalid status. Use PENDING_PAYMENT, PAID, PROCESSING, SHIPPED, DELIVERED, CANCELLED, PAYMENT_FAILED or REFUNDED");
        }
        // Guard payment-driven states: they are set by PaymentService, not admin shortcuts.
        // Admin may still set them explicitly if needed, but warn via normal flow.
        order.setStatus(next);
        orderRepo.save(order);
        if (order.getUser() != null && order.getUser().getUsername() != null) {
            notificationService.notify(order.getUser().getUsername(), "ORDER_STATUS",
                    order.getOrderNumber() == null ? ("#" + order.getId()) : order.getOrderNumber(),
                    next.name(), "/orders");
        }
        return toDetail(order);
    }

    private String formatTotal(BigDecimal total) {
        try {
            String currency = settingService.getAllAsMap().getOrDefault("currency", "IRR");
            if ("TOMAN".equalsIgnoreCase(currency)) {
                long toman = total.divide(BigDecimal.TEN, 0, RoundingMode.HALF_UP).longValue();
                return String.format("%,d Toman", toman);
            }
        } catch (Exception ignored) {
            // fall through to IRR
        }
        return total.toPlainString() + " IRR";
    }

    private Map<String, Object> toSummary(Order order) {
        List<OrderItem> items = order.getItems() != null && !order.getItems().isEmpty()
                ? order.getItems()
                : orderItemRepo.findByOrder(order);
        int count = items.stream().mapToInt(OrderItem::getQuantity).sum();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", order.getId());
        m.put("orderNumber", order.getOrderNumber());
        m.put("accessToken", order.getAccessToken());
        m.put("status", order.getStatus().name());
        m.put("total", order.getTotalAmount());
        m.put("itemCount", count);
        m.put("orderedAt", order.getOrderedAt());
        if (order.getUser() != null) {
            m.put("username", order.getUser().getUsername());
        }
        return m;
    }

    private Map<String, Object> toDetail(Order order) {
        Map<String, Object> m = toSummary(order);
        m.put("customerName", order.getCustomerName());
        m.put("customerPhone", order.getCustomerPhone());
        m.put("customerAddress", order.getCustomerAddress());
        m.put("customerEmail", order.getCustomerEmail());
        m.put("paidRefId", order.getPaidRefId());
        List<OrderItem> items = order.getItems() != null && !order.getItems().isEmpty()
                ? order.getItems()
                : orderItemRepo.findByOrder(order);
        List<Map<String, Object>> lines = items.stream().map(item -> {
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("id", item.getId());
            if (item.getProduct() != null) {
                line.put("productId", item.getProduct().getId());
                line.put("slug", item.getProduct().getSlug());
                line.put("imageUrl", item.getProduct().getImageUrl());
                line.put("bookType", ProductService.normalizeBookType(item.getProduct().getBookType()));
                line.put("fixedQty", ProductService.isEbook(item.getProduct()));
            } else {
                line.put("productId", null);
                line.put("slug", null);
                line.put("imageUrl", null);
                line.put("bookType", "PDF");
                line.put("fixedQty", true);
            }
            line.put("name", item.getProductName());
            line.put("price", item.getUnitPrice());
            line.put("qty", item.getQuantity());
            line.put("quantity", item.getQuantity());
            if (item.getUnitPrice() != null) {
                line.put("lineTotal", item.lineTotal());
            }
            return line;
        }).toList();
        m.put("items", lines);
        return m;
    }
}
