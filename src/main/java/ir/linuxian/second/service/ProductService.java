package ir.linuxian.second.service;

import ir.linuxian.second.entities.product.Category;
import ir.linuxian.second.entities.product.Product;
import ir.linuxian.second.repos.CategoryRepo;
import ir.linuxian.second.repos.ProductRepo;
import ir.linuxian.second.repos.ProductViewRepo;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class ProductService {

    private final ProductRepo productRepo;
    private final CategoryRepo categoryRepo;
    private final MenuItemService menuItemService;
    private final ProductViewRepo productViewRepo;

    public ProductService(ProductRepo productRepo, CategoryRepo categoryRepo,
                          MenuItemService menuItemService, ProductViewRepo productViewRepo) {
        this.productRepo = productRepo;
        this.categoryRepo = categoryRepo;
        this.menuItemService = menuItemService;
        this.productViewRepo = productViewRepo;
    }

    public List<Product> findActive() {
        return productRepo.findByActiveTrueOrderByCreatedAtDesc();
    }

    public List<Product> findAll() {
        return productRepo.findAll();
    }

    public Product findBySlug(String slug) {
        return productRepo.findBySlug(slug)
                .orElseThrow(() -> new RuntimeException("Product not found: " + slug));
    }

    @Transactional
    public Product findBySlugAndCountView(String slug) {
        return findBySlugAndCountView(slug, null, null);
    }

    /**
     * Returns the product, counting a view only once per viewer: per
     * username for logged-in users, per viewer token for guests.
     * A guest view is adopted (not recounted) when its token session
     * logs in. Concurrent first views (e.g. StrictMode double-fetch)
     * count exactly once via an atomic upsert.
     */
    @Transactional
    public Product findBySlugAndCountView(String slug, String username, String viewerToken) {
        Product product = findBySlug(slug);
        String userKey = viewerKey("u", username);
        String tokenKey = viewerKey("t", viewerToken);
        if (userKey == null && tokenKey == null) {
            // Unattributable visit (no login, no token): show, don't count.
            return product;
        }
        boolean fresh;
        if (userKey != null) {
            // Adopt the guest view of a just-logged-in token session instead of counting again.
            boolean hadGuestView = tokenKey != null
                    && productViewRepo.deleteByProductAndKey(product.getId(), tokenKey) > 0;
            fresh = productViewRepo.insertIgnoreConflict(product.getId(), userKey) == 1 && !hadGuestView;
        } else {
            fresh = productViewRepo.insertIgnoreConflict(product.getId(), tokenKey) == 1;
        }
        if (fresh) {
            product.setViewCount(nz(product.getViewCount()) + 1);
            return productRepo.save(product);
        }
        return product;
    }

    private static String viewerKey(String prefix, String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return prefix + ":" + raw.trim();
    }

    @Transactional
    public Product react(Long id, String action) {
        Product product = productRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found: " + id));
        switch (action == null ? "" : action) {
            case "like" -> product.setLikeCount(nz(product.getLikeCount()) + 1);
            case "unlike" -> product.setLikeCount(Math.max(0, nz(product.getLikeCount()) - 1));
            case "want" -> product.setWantCount(nz(product.getWantCount()) + 1);
            case "unwant" -> product.setWantCount(Math.max(0, nz(product.getWantCount()) - 1));
            case "up" -> product.setRecommendUp(nz(product.getRecommendUp()) + 1);
            case "unup" -> product.setRecommendUp(Math.max(0, nz(product.getRecommendUp()) - 1));
            case "down" -> product.setRecommendDown(nz(product.getRecommendDown()) + 1);
            case "undown" -> product.setRecommendDown(Math.max(0, nz(product.getRecommendDown()) - 1));
            default -> throw new RuntimeException("Unknown reaction: " + action);
        }
        return productRepo.save(product);
    }

    private static long nz(Long v) {
        return v == null ? 0L : v;
    }

    public static BigDecimal effectivePrice(Product product) {
        BigDecimal base = product.getPrice() == null ? BigDecimal.ZERO : product.getPrice();
        int d = clampDiscount(product.getDiscountPercent());
        if (d <= 0) return base.setScale(0, RoundingMode.HALF_UP);
        return base.multiply(BigDecimal.valueOf(100 - d))
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
    }

    public static int clampDiscount(Integer d) {
        if (d == null) return 0;
        return Math.max(0, Math.min(100, d));
    }

    public static String normalizeBookType(String t) {
        if (t == null) return "PDF";
        String s = t.trim().toUpperCase();
        if (s.equals("PAPER") || s.equals("PAPERBOOK") || s.equals("PHYSICAL")) return "PAPER";
        if (s.equals("PDF") || s.equals("DIGITAL") || s.equals("EBOOK") || s.equals("E-BOOK")) return "PDF";
        return "PDF";
    }

    /** E-books (PDF/digital) are always limited to 1 per order. */
    public static boolean isEbook(Product product) {
        if (product == null) return false;
        return !"PAPER".equals(normalizeBookType(product.getBookType()));
    }

    /** Effective per-order limit: always 1 for e-books, otherwise the stored value. */
    public static int effectiveMaxPerOrder(Product product) {
        if (product == null) return 1;
        if (isEbook(product)) return 1;
        Integer m = product.getMaxPerOrder();
        if (m == null || m < 1) return 1;
        return m;
    }

    public static boolean needsAddress(List<Product> products) {
        if (products == null) return true;
        return products.stream().anyMatch(p -> "PAPER".equals(normalizeBookType(p.getBookType())));
    }

    public Product create(Product product) {
        if (product.getSlug() == null || product.getSlug().isBlank()) {
            product.setSlug(slugify(product.getName()));
        }
        product.setBookType(normalizeBookType(product.getBookType()));
        if (isEbook(product)) {
            // E-books are immutable: always 1 per order.
            product.setMaxPerOrder(1);
        } else if (product.getMaxPerOrder() == null || product.getMaxPerOrder() < 1) {
            product.setMaxPerOrder(1);
        }
        if (productRepo.existsBySlug(product.getSlug())) {
            throw new RuntimeException("Product slug already exists: " + product.getSlug());
        }
        product.setDiscountPercent(clampDiscount(product.getDiscountPercent()));
        product.setCategory(resolveCategory(product.getCategory()));
        Product saved = productRepo.save(product);
        // E-books land under Shop -> Books -> E-books, paper books under Paper books.
        menuItemService.syncProductOnCreate(saved);
        return saved;
    }

    public Product update(Long id, Product incoming) {
        Product existing = productRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found: " + id));
        String oldSlug = existing.getSlug();
        String oldName = existing.getName();
        existing.setName(incoming.getName());
        existing.setDescription(incoming.getDescription());
        existing.setPrice(incoming.getPrice());
        existing.setImageUrl(incoming.getImageUrl());
        existing.setStock(incoming.getStock());
        existing.setActive(incoming.getActive());
        existing.setCategory(resolveCategory(incoming.getCategory()));
        existing.setAuthor(incoming.getAuthor());
        existing.setTranslator(incoming.getTranslator());
        existing.setPublisher(incoming.getPublisher());
        existing.setIsbn(incoming.getIsbn());
        existing.setLanguage(incoming.getLanguage());
        existing.setCoverType(incoming.getCoverType());
        existing.setBookSize(incoming.getBookSize());
        existing.setPageCount(incoming.getPageCount());
        existing.setEdition(incoming.getEdition());
        existing.setWeightGrams(incoming.getWeightGrams());
        existing.setPublishYear(incoming.getPublishYear());
        existing.setDiscountPercent(clampDiscount(incoming.getDiscountPercent()));
        if (incoming.getBookType() != null && !incoming.getBookType().isBlank()) {
            existing.setBookType(normalizeBookType(incoming.getBookType()));
        }
        if (isEbook(existing)) {
            // E-books are immutable: always 1 per order, ignore any incoming value.
            existing.setMaxPerOrder(1);
        } else if (incoming.getMaxPerOrder() != null && incoming.getMaxPerOrder() >= 1) {
            existing.setMaxPerOrder(incoming.getMaxPerOrder());
        }
        if (incoming.getSlug() != null && !incoming.getSlug().isBlank() && !incoming.getSlug().equals(existing.getSlug())) {
            if (productRepo.existsBySlug(incoming.getSlug())) {
                throw new RuntimeException("Product slug already exists: " + incoming.getSlug());
            }
            existing.setSlug(incoming.getSlug());
        }
        Product saved = productRepo.save(existing);
        menuItemService.syncProductOnUpdate(oldSlug, oldName, saved);
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        String slug = productRepo.findById(id).map(Product::getSlug).orElse(null);
        if (slug != null) {
            menuItemService.syncProductOnDelete(slug);
        }
        // View rows reference the product; drop them first (FK).
        productViewRepo.deleteByProduct(id);
        productRepo.deleteById(id);
    }

    private Category resolveCategory(Category ref) {
        if (ref == null || ref.getId() == null) return null;
        return categoryRepo.findById(ref.getId())
                .orElseThrow(() -> new RuntimeException("Category not found: " + ref.getId()));
    }

    private String slugify(String name) {
        if (name == null) return "product-" + System.currentTimeMillis();
        String slug = name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return slug.isBlank() ? "product-" + System.currentTimeMillis() : slug;
    }
}
