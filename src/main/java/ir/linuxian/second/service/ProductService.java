package ir.linuxian.second.service;

import ir.linuxian.second.entities.product.Product;
import ir.linuxian.second.repos.ProductRepo;
import ir.linuxian.second.util.SlugUtil;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductService {

    private final ProductRepo productRepo;

    public ProductService(ProductRepo productRepo) {
        this.productRepo = productRepo;
    }

    public List<Product> findAll() {
        return productRepo.findAllByOrderByCreatedAtDesc();
    }

    public Product findBySlug(String slug) {
        return productRepo.findBySlug(slug)
                .orElseThrow(() -> new RuntimeException("Product not found: " + slug));
    }

    public Product create(Product product) {
        product.setSlug(uniqueSlug(baseSlug(product), null));
        return productRepo.save(product);
    }

    public Product update(Long id, Product changes) {
        Product product = productRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found: " + id));
        product.setName(changes.getName());
        product.setDescription(changes.getDescription());
        product.setPrice(changes.getPrice());
        product.setImageUrl(changes.getImageUrl());
        product.setCategory(changes.getCategory());
        product.setStock(changes.getStock());
        product.setFeatured(changes.isFeatured());
        String desired = baseSlug(changes);
        if (!desired.isBlank() && !desired.equals(product.getSlug())) {
            product.setSlug(uniqueSlug(desired, id));
        }
        return productRepo.save(product);
    }

    public void delete(Long id) {
        productRepo.deleteById(id);
    }

    private String baseSlug(Product product) {
        String slug = product.getSlug();
        if (slug == null || slug.isBlank()) {
            slug = SlugUtil.slugify(product.getName());
        } else {
            slug = SlugUtil.slugify(slug);
        }
        return slug.isBlank() ? "product" : slug;
    }

    private String uniqueSlug(String base, Long ignoreId) {
        String candidate = base;
        int counter = 1;
        while (true) {
            Product existing = productRepo.findBySlug(candidate).orElse(null);
            if (existing == null || existing.getId().equals(ignoreId)) {
                return candidate;
            }
            candidate = base + "-" + counter++;
        }
    }
}
