package ir.linuxian.second.service;

import ir.linuxian.second.entities.product.Category;
import ir.linuxian.second.entities.product.Product;
import ir.linuxian.second.repos.CategoryRepo;
import ir.linuxian.second.repos.ProductRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CategoryService {

    private final CategoryRepo categoryRepo;
    private final ProductRepo productRepo;

    public CategoryService(CategoryRepo categoryRepo, ProductRepo productRepo) {
        this.categoryRepo = categoryRepo;
        this.productRepo = productRepo;
    }

    public List<Category> findAll() {
        return categoryRepo.findAllByOrderByNameAsc();
    }

    public Category findBySlug(String slug) {
        return categoryRepo.findBySlug(slug)
                .orElseThrow(() -> new RuntimeException("Category not found: " + slug));
    }

    public Category create(Category category) {
        if (category.getName() == null || category.getName().isBlank()) {
            throw new RuntimeException("Category name is required");
        }
        if (category.getSlug() == null || category.getSlug().isBlank()) {
            category.setSlug(slugify(category.getName()));
        }
        if (categoryRepo.existsBySlug(category.getSlug())) {
            throw new RuntimeException("Category slug already exists: " + category.getSlug());
        }
        return categoryRepo.save(category);
    }

    public Category update(Long id, Category incoming) {
        Category existing = categoryRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Category not found: " + id));
        if (incoming.getName() != null && !incoming.getName().isBlank()) {
            existing.setName(incoming.getName());
        }
        existing.setNameFa(incoming.getNameFa());
        existing.setDescription(incoming.getDescription());
        existing.setImageUrl(incoming.getImageUrl());
        if (incoming.getSlug() != null && !incoming.getSlug().isBlank()
                && !incoming.getSlug().equals(existing.getSlug())) {
            if (categoryRepo.existsBySlug(incoming.getSlug())) {
                throw new RuntimeException("Category slug already exists: " + incoming.getSlug());
            }
            existing.setSlug(incoming.getSlug());
        }
        return categoryRepo.save(existing);
    }

    @Transactional
    public void delete(Long id) {
        Category existing = categoryRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Category not found: " + id));
        // Keep products, just uncategorize them.
        List<Product> products = productRepo.findByCategoryId(existing.getId());
        for (Product p : products) {
            p.setCategory(null);
        }
        productRepo.saveAll(products);
        categoryRepo.delete(existing);
    }

    private String slugify(String name) {
        if (name == null) return "category-" + System.currentTimeMillis();
        String slug = name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return slug.isBlank() ? "category-" + System.currentTimeMillis() : slug;
    }
}
