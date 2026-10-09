package ir.linuxian.second.service;

import ir.linuxian.second.entities.page.Page;
import ir.linuxian.second.repos.PageRepo;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class PageService {

    private final PageRepo pageRepo;

    public PageService(PageRepo pageRepo) {
        this.pageRepo = pageRepo;
    }
    public Page  findBySlug(String slug) {
       return pageRepo.findBySlug(slug).orElseThrow(() -> new RuntimeException("Page not found! " + slug));
    }
}
