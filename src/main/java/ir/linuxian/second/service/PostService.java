package ir.linuxian.second.service;

import ir.linuxian.second.entities.post.Post;
import ir.linuxian.second.repos.PostRepo;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PostService {

    private final PostRepo postRepo;

    public PostService(PostRepo postRepo) {
        this.postRepo = postRepo;
    }

    public List<Post> findPublished() {
        return postRepo.findByStatusOrderByCreatedAtDesc("PUBLISHED");
    }

    public List<Post> findAll() {
        return postRepo.findAll();
    }

    public Post findBySlug(String slug) {
        return postRepo.findBySlug(slug)
                .orElseThrow(() -> new RuntimeException("Post not found: " + slug));
    }

    public Post create(Post post) {
        if (post.getSlug() == null || post.getSlug().isBlank()) {
            post.setSlug(slugify(post.getTitle()));
        }
        if (postRepo.existsBySlug(post.getSlug())) {
            throw new RuntimeException("Post slug already exists: " + post.getSlug());
        }
        if (post.getStatus() == null) {
            post.setStatus("PUBLISHED");
        }
        return postRepo.save(post);
    }

    public Post update(Long id, Post incoming) {
        Post existing = postRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Post not found: " + id));
        existing.setTitle(incoming.getTitle());
        existing.setExcerpt(incoming.getExcerpt());
        existing.setContent(incoming.getContent());
        existing.setCoverImage(incoming.getCoverImage());
        existing.setStatus(incoming.getStatus() == null ? existing.getStatus() : incoming.getStatus());
        if (incoming.getSlug() != null && !incoming.getSlug().isBlank() && !incoming.getSlug().equals(existing.getSlug())) {
            if (postRepo.existsBySlug(incoming.getSlug())) {
                throw new RuntimeException("Post slug already exists: " + incoming.getSlug());
            }
            existing.setSlug(incoming.getSlug());
        }
        return postRepo.save(existing);
    }

    public void delete(Long id) {
        postRepo.deleteById(id);
    }

    private String slugify(String title) {
        if (title == null) return "post-" + System.currentTimeMillis();
        String slug = title.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return slug.isBlank() ? "post-" + System.currentTimeMillis() : slug;
    }
}
