package ir.linuxian.second.repos;

import ir.linuxian.second.entities.post.Post;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PostRepo extends JpaRepository<Post, Long> {
    Optional<Post> findBySlug(String slug);
    List<Post> findByStatusOrderByCreatedAtDesc(String status);
    boolean existsBySlug(String slug);
}
