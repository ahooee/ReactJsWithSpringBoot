package ir.linuxian.second.repos;

import ir.linuxian.second.entities.product.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.List;
import java.util.Optional;

@RepositoryRestResource(exported = false)
public interface ProductRepo extends JpaRepository<Product, Long> {

    Optional<Product> findBySlug(String slug);

    List<Product> findAllByOrderByCreatedAtDesc();
}
