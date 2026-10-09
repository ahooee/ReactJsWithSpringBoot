package ir.linuxian.second.service;

import ir.linuxian.second.entities.page.Page;
import ir.linuxian.second.repos.PageRepo;
import ir.linuxian.second.util.SlugUtil;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PageService {

    private final PageRepo pageRepo;

    public PageService(PageRepo pageRepo) {
        this.pageRepo = pageRepo;
    }

    public List<Page> findAll() {
        return pageRepo.findAll();
    }

    public Page findBySlug(String slug) {
        return pageRepo.findBySlug(slug).orElseThrow(() -> new RuntimeException("Page not found! " + slug));
    }

    public Page create(Page page) {
        page.setSlug(uniqueSlug(baseSlug(page), null));
        return pageRepo.save(page);
    }

    public Page update(Long id, Page changes) {
        Page page = pageRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Page not found! " + id));
        page.setTitle(changes.getTitle());
        page.setContent(changes.getContent());
        String desired = baseSlug(changes);
        if (!desired.isBlank() && !desired.equals(page.getSlug())) {
            page.setSlug(uniqueSlug(desired, id));
        }
        return pageRepo.save(page);
    }

    public void delete(Long id) {
        pageRepo.deleteById(id);
    }

    private String baseSlug(Page page) {
        String slug = page.getSlug();
        if (slug == null || slug.isBlank()) {
            slug = SlugUtil.slugify(page.getTitle());
        } else {
            slug = SlugUtil.slugify(slug);
        }
        return slug.isBlank() ? "page" : slug;
    }

    private String uniqueSlug(String base, Long ignoreId) {
        String candidate = base;
        int counter = 1;
        while (true) {
            Page existing = pageRepo.findBySlug(candidate).orElse(null);
            if (existing == null || existing.getId().equals(ignoreId)) {
                return candidate;
            }
            candidate = base + "-" + counter++;
        }
    }
}
