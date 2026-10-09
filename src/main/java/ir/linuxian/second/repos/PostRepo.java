package ir.linuxian.second.repos;

import ir.linuxian.second.entities.post.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.List;
import java.util.Optional;

@RepositoryRestResource(exported = false)
public interface PostRepo extends JpaRepository<Post, Long> {

    Optional<Post> findBySlug(String slug);

    List<Post> findByStatusOrderByCreatedAtDesc(String status);

    List<Post> findAllByOrderByCreatedAtDesc();
}
