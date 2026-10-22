package ir.linuxian.second.repos;

import ir.linuxian.second.entities.product.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductRepo extends JpaRepository<Product, Long> {
    Optional<Product> findBySlug(String slug);
    List<Product> findByActiveTrueOrderByCreatedAtDesc();
    List<Product> findByCategoryId(Long categoryId);
    boolean existsBySlug(String slug);
}
