package ir.linuxian.second.service;

import ir.linuxian.second.entities.post.Post;
import ir.linuxian.second.repos.PostRepo;
import ir.linuxian.second.util.SlugUtil;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PostService {

    private final PostRepo postRepo;

    public PostService(PostRepo postRepo) {
        this.postRepo = postRepo;
    }

    public List<Post> findAll() {
        return postRepo.findAllByOrderByCreatedAtDesc();
    }

    public List<Post> findPublished() {
        return postRepo.findByStatusOrderByCreatedAtDesc("published");
    }

    public Post findBySlug(String slug) {
        return postRepo.findBySlug(slug)
                .orElseThrow(() -> new RuntimeException("Post not found: " + slug));
    }

    public Post create(Post post) {
        post.setSlug(uniqueSlug(baseSlug(post), null));
        return postRepo.save(post);
    }

    public Post update(Long id, Post changes) {
        Post post = postRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Post not found: " + id));
        post.setTitle(changes.getTitle());
        post.setExcerpt(changes.getExcerpt());
        post.setContent(changes.getContent());
        post.setCoverImage(changes.getCoverImage());
        post.setAuthor(changes.getAuthor());
        if (changes.getStatus() != null && !changes.getStatus().isBlank()) {
            post.setStatus(changes.getStatus());
        }
        String desired = baseSlug(changes);
        if (!desired.isBlank() && !desired.equals(post.getSlug())) {
            post.setSlug(uniqueSlug(desired, id));
        }
        return postRepo.save(post);
    }

    public void delete(Long id) {
        postRepo.deleteById(id);
    }

    private String baseSlug(Post post) {
        String slug = post.getSlug();
        if (slug == null || slug.isBlank()) {
            slug = SlugUtil.slugify(post.getTitle());
        } else {
            slug = SlugUtil.slugify(slug);
        }
        return slug.isBlank() ? "post" : slug;
    }

    private String uniqueSlug(String base, Long ignoreId) {
        String candidate = base;
        int counter = 1;
        while (true) {
            Post existing = postRepo.findBySlug(candidate).orElse(null);
            if (existing == null || existing.getId().equals(ignoreId)) {
                return candidate;
            }
            candidate = base + "-" + counter++;
        }
    }
}
