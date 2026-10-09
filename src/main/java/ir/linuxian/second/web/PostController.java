package ir.linuxian.second.web;

import ir.linuxian.second.entities.post.Post;
import ir.linuxian.second.service.PostService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/posts")
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    /** Public feed: published posts only. */
    @GetMapping
    public List<Post> getPublishedPosts() {
        return postService.findPublished();
    }

    /** Admin feed: every post including drafts. */
    @GetMapping("/all")
    public List<Post> getAllPosts() {
        return postService.findAll();
    }

    @GetMapping("/{slug}")
    public Post getPostBySlug(@PathVariable String slug) {
        return postService.findBySlug(slug);
    }

    @PostMapping
    public Post createPost(@RequestBody Post post) {
        return postService.create(post);
    }

    @PutMapping("/{id}")
    public Post updatePost(@PathVariable Long id, @RequestBody Post post) {
        return postService.update(id, post);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletePost(@PathVariable Long id) {
        postService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
