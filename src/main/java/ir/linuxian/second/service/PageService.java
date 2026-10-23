package ir.linuxian.second.service;

import ir.linuxian.second.entities.page.Page;
import ir.linuxian.second.repos.PageRepo;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PageService {

    private final PageRepo pageRepo;

    public PageService(PageRepo pageRepo) {
        this.pageRepo = pageRepo;
    }

    public Page findBySlug(String slug) {
        return pageRepo.findBySlug(slug).orElseThrow(() -> new RuntimeException("Page not found! " + slug));
    }

    public List<Page> findAll() {
        return pageRepo.findAll();
    }

    public Page create(Page page) {
        if (page.getSlug() == null || page.getSlug().isBlank()) {
            throw new RuntimeException("Page slug is required");
        }
        if (pageRepo.findBySlug(page.getSlug()).isPresent()) {
            throw new RuntimeException("Page slug already exists: " + page.getSlug());
        }
        if (page.getStatus() == null) {
            page.setStatus("PUBLISHED");
        }
        return pageRepo.save(page);
    }

    public Page update(Long id, Page incoming) {
        Page existing = pageRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Page not found: " + id));
        existing.setTitle(incoming.getTitle());
        existing.setContent(incoming.getContent());
        existing.setTheme(incoming.getTheme());
        if (incoming.getStatus() != null) {
            existing.setStatus(incoming.getStatus());
        }
        if (incoming.getSlug() != null && !incoming.getSlug().equals(existing.getSlug())) {
            if (pageRepo.findBySlug(incoming.getSlug()).isPresent()) {
                throw new RuntimeException("Page slug already exists: " + incoming.getSlug());
            }
            existing.setSlug(incoming.getSlug());
        }
        return pageRepo.save(existing);
    }

    public void delete(Long id) {
        pageRepo.deleteById(id);
    }
}
