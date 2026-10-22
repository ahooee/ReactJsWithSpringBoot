package ir.linuxian.second.repos;

import ir.linuxian.second.entities.product.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ReviewRepo extends JpaRepository<Review, Long> {
    List<Review> findByProductIdOrderByCreatedAtDesc(Long productId);

    /** Books recommended: best average rating per library book. */
    @Query("select p.bookId, avg(rv.rating) from Review rv join rv.product p "
            + "where p.bookId is not null group by p.bookId order by avg(rv.rating) desc, count(rv) desc")
    List<Object[]> topRatedBookIds();
}
