package ir.linuxian.second.repos;

import ir.linuxian.second.entities.product.ProductView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ProductViewRepo extends JpaRepository<ProductView, Long> {

    /**
     * Atomic first-view registration. Inserts the (product, viewer) row
     * unless it already exists.
     *
     * @return 1 when this call counted a new view, 0 when already viewed.
     */
    @Modifying
    @Query(value = "INSERT INTO product_views(product_id, viewer_key, viewed_at) " +
            "VALUES (:productId, :key, now()) ON CONFLICT (product_id, viewer_key) DO NOTHING",
            nativeQuery = true)
    int insertIgnoreConflict(@Param("productId") Long productId, @Param("key") String key);

    @Modifying
    @Query("DELETE FROM ProductView v WHERE v.product.id = :productId AND v.viewerKey = :key")
    int deleteByProductAndKey(@Param("productId") Long productId, @Param("key") String key);

    @Modifying
    @Transactional
    @Query("DELETE FROM ProductView v WHERE v.product.id = :productId")
    int deleteByProduct(@Param("productId") Long productId);
}
