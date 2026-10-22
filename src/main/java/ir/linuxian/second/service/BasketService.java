package ir.linuxian.second.service;

import ir.linuxian.second.entities.shopping.basket.Basket;
import ir.linuxian.second.entities.shopping.basket.BasketItem;
import ir.linuxian.second.entities.shopping.basket.BasketStatus;
import ir.linuxian.second.entities.product.Product;
import ir.linuxian.second.repos.BasketItemRepo;
import ir.linuxian.second.repos.BasketRepo;
import ir.linuxian.second.repos.ProductRepo;
import ir.linuxian.second.repos.UserRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Basket-only logic: guest/logged-in cart editing.
 * Checkout (basket -> order snapshot + stock decrement) lives in
 * {@link OrderService#checkout}. This class never touches orders/payments.
 */
@Service
public class BasketService {

    private final BasketRepo basketRepo;
    private final BasketItemRepo itemRepo;
    private final ProductRepo productRepo;
    private final UserRepo userRepo;

    public BasketService(BasketRepo basketRepo, BasketItemRepo itemRepo,
                         ProductRepo productRepo, UserRepo userRepo) {
        this.basketRepo = basketRepo;
        this.itemRepo = itemRepo;
        this.productRepo = productRepo;
        this.userRepo = userRepo;
    }

    @Transactional
    public Map<String, Object> getOrCreate(String token, String username) {
        Basket basket = resolve(token);
        attachUser(basket, username);
        return toResponse(basket);
    }

    /** Resolve an OPEN basket by token, or create a fresh one. */
    @Transactional
    public Basket resolveBasket(String token) {
        return resolve(token);
    }

    @Transactional
    public Map<String, Object> addItem(String token, String username, Long productId, int qty) {
        Product product = productRepo.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found: " + productId));
        if (!Boolean.TRUE.equals(product.getActive())) {
            throw new RuntimeException(product.getName() + " is not available");
        }
        int cap = capFor(product);
        if (cap <= 0) {
            throw new RuntimeException(product.getName() + " is out of stock");
        }
        int want = qty <= 0 ? 1 : qty;
        if (ProductService.isEbook(product)) {
            // E-books are immutable: always exactly 1 per order.
            want = 1;
        }

        Basket basket = resolve(token);
        attachUser(basket, username);

        BasketItem item = itemRepo.findByBasketAndProduct(basket, product).orElse(null);
        if (item == null) {
            item = new BasketItem(basket, product, Math.min(want, cap));
        } else if (ProductService.isEbook(product)) {
            item.setQty(1);
        } else {
            item.setQty(Math.min(item.getQty() + want, cap));
        }
        itemRepo.save(item);

        return toResponse(basket);
    }

    @Transactional
    public Map<String, Object> setQty(String token, String username, Long itemId, int qty) {
        Basket basket = resolve(token);
        attachUser(basket, username);

        BasketItem item = itemRepo.findById(itemId)
                .orElseThrow(() -> new RuntimeException("Basket item not found: " + itemId));
        if (!item.getBasket().getId().equals(basket.getId())) {
            throw new RuntimeException("Basket item not found: " + itemId);
        }

        if (qty <= 0) {
            itemRepo.delete(item);
        } else {
            int cap = capFor(item.getProduct());
            if (cap <= 0) {
                itemRepo.delete(item);
            } else if (ProductService.isEbook(item.getProduct())) {
                // E-books are immutable: always exactly 1.
                item.setQty(1);
                itemRepo.save(item);
            } else {
                item.setQty(Math.min(qty, cap));
                itemRepo.save(item);
            }
        }

        return toResponse(basket);
    }

    @Transactional
    public Map<String, Object> removeItem(String token, String username, Long itemId) {
        Basket basket = resolve(token);
        attachUser(basket, username);

        BasketItem item = itemRepo.findById(itemId)
                .orElseThrow(() -> new RuntimeException("Basket item not found: " + itemId));
        if (!item.getBasket().getId().equals(basket.getId())) {
            throw new RuntimeException("Basket item not found: " + itemId);
        }
        itemRepo.delete(item);

        return toResponse(basket);
    }

    @Transactional
    public Map<String, Object> clear(String token, String username) {
        Basket basket = resolve(token);
        attachUser(basket, username);
        itemRepo.deleteByBasket(basket);
        return toResponse(basket);
    }

    /** Mark a basket as checked-out after an order snapshot was created. */
    @Transactional
    public void markCheckedOut(Basket basket) {
        basket.setStatus(BasketStatus.CHECKED_OUT);
        basketRepo.save(basket);
    }

    private Basket resolve(String token) {
        if (token != null && !token.isBlank()) {
            Basket found = basketRepo.findByToken(token).orElse(null);
            if (found != null && found.getStatus() == BasketStatus.OPEN) {
                return found;
            }
        }
        Basket basket = new Basket();
        basket.setToken(UUID.randomUUID().toString());
        basket.setStatus(BasketStatus.OPEN);
        return basketRepo.save(basket);
    }

    private void attachUser(Basket basket, String username) {
        if (username == null || username.isBlank() || basket.getUser() != null) {
            return;
        }
        userRepo.findByUsername(username).ifPresent(user -> {
            basket.setUser(user);
            basketRepo.save(basket);
        });
    }

    private int capFor(Product product) {
        int stock = product.getStock() == null ? 0 : product.getStock();
        if (stock <= 0) {
            return 0;
        }
        int limit = ProductService.effectiveMaxPerOrder(product);
        return Math.max(1, Math.min(limit < 1 ? 1 : limit, stock));
    }

    private Map<String, Object> toResponse(Basket basket) {
        List<BasketItem> items = itemRepo.findByBasket(basket);
        List<Map<String, Object>> lines = items.stream().map(item -> {
            Product p = item.getProduct();
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("id", item.getId());
            line.put("productId", p.getId());
            line.put("slug", p.getSlug());
            line.put("name", p.getName());
            line.put("price", ProductService.effectivePrice(p));
            line.put("oldPrice", p.getPrice());
            line.put("bookType", ProductService.normalizeBookType(p.getBookType()));
            line.put("discountPercent", ProductService.clampDiscount(p.getDiscountPercent()));
            line.put("imageUrl", p.getImageUrl());
            line.put("stock", p.getStock());
            line.put("maxPerOrder", ProductService.effectiveMaxPerOrder(p));
            line.put("fixedQty", ProductService.isEbook(p));
            line.put("qty", item.getQty());
            return line;
        }).toList();

        BigDecimal subtotal = BigDecimal.ZERO;
        int count = 0;
        for (BasketItem item : items) {
            subtotal = subtotal.add(ProductService.effectivePrice(item.getProduct())
                    .multiply(BigDecimal.valueOf(item.getQty())));
            count += item.getQty();
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("token", basket.getToken());
        response.put("status", basket.getStatus().name());
        response.put("items", lines);
        response.put("count", count);
        response.put("subtotal", subtotal);
        response.put("needsAddress", items.stream()
                .map(BasketItem::getProduct)
                .anyMatch(p -> "PAPER".equals(ProductService.normalizeBookType(p == null ? null : p.getBookType()))));
        return response;
    }
}
