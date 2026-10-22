package ir.linuxian.second.service;

import ir.linuxian.second.entities.product.Product;
import ir.linuxian.second.entities.product.Review;
import ir.linuxian.second.repos.ProductRepo;
import ir.linuxian.second.repos.ReviewRepo;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ReviewService {

    private final ReviewRepo reviewRepo;
    private final ProductRepo productRepo;

    public ReviewService(ReviewRepo reviewRepo, ProductRepo productRepo) {
        this.reviewRepo = reviewRepo;
        this.productRepo = productRepo;
    }

    public List<Review> list(Long productId) {
        return reviewRepo.findByProductIdOrderByCreatedAtDesc(productId);
    }

    public Review add(Long productId, String username, Integer rating, String text) {
        Product product = productRepo.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found: " + productId));
        int r = rating == null ? 0 : rating;
        if (r < 1 || r > 5) {
            throw new RuntimeException("Rating must be between 1 and 5");
        }
        if (text == null || text.isBlank()) {
            throw new RuntimeException("Review text is required");
        }
        Review review = new Review();
        review.setProduct(product);
        review.setUsername(username);
        review.setRating(r);
        review.setText(text.trim());
        return reviewRepo.save(review);
    }

    public void delete(Long id) {
        if (!reviewRepo.existsById(id)) {
            throw new RuntimeException("Review not found: " + id);
        }
        reviewRepo.deleteById(id);
    }
}
