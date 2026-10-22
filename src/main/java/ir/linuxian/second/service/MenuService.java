package ir.linuxian.second.service;

import ir.linuxian.second.dto.MenuItemResponse;
import ir.linuxian.second.dto.MenuResponse;
import ir.linuxian.second.entities.menu.Menu;
import ir.linuxian.second.entities.menu.MenuItem;
import ir.linuxian.second.repos.MenuItemRepo;
import ir.linuxian.second.repos.MenuRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MenuService {

    private final MenuRepo menuRepo;
    private final MenuItemRepo menuItemRepo;

    public MenuService(MenuRepo menuRepo, MenuItemRepo menuItemRepo) {
        this.menuRepo = menuRepo;
        this.menuItemRepo = menuItemRepo;
    }

    @Transactional(readOnly = true)
    public List<MenuResponse> getAllMenus(){
        return menuRepo.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public MenuResponse getMenuById(Long menuId){
        Menu menu = menuRepo.findById(menuId).orElseThrow(()->new RuntimeException("Menu not found"+menuId));

        return toResponse(menu);
    }

    @Transactional(readOnly = true)
    public MenuResponse getMenuBySlug(String slug){
        Menu menu = menuRepo.findBySlug(slug).orElseThrow(()->new RuntimeException("Menu not found! "+slug));

        return toResponse(menu);
    }

    public MenuResponse addMenu(Menu menu){
        if (menu.getUrl() == null || menu.getUrl().isBlank()) {
            menu.setUrl(defaultUrlForSlug(menu.getSlug()));
        }
        return toResponse(menuRepo.save(menu));
    }

    public MenuResponse updateMenu(Long menuId,Menu menu){
        Menu oldMenu = menuRepo.getMenuById(menuId);
        if (menu.getSlug() != null && !menu.getSlug().isBlank()) {
            oldMenu.setSlug(menu.getSlug());
        }
        if (menu.getName() != null && !menu.getName().isBlank()) {
            oldMenu.setName(menu.getName());
        }
        if (menu.getNameFa() != null) {
            oldMenu.setNameFa(menu.getNameFa());
        }
        if (menu.getUrl() != null && !menu.getUrl().isBlank()) {
            oldMenu.setUrl(menu.getUrl());
        }
        return toResponse(menuRepo.save(oldMenu));
    }

    @Transactional
    public boolean deleteMenuById(Long menuId){
        if(!menuRepo.existsById(menuId)){
            return false;
        }
        // Delete items deepest-first without initializing menu.items,
        // so nested (parent_id) rows never violate the FK.
        List<MenuItem> doomed = new java.util.ArrayList<>();
        for (MenuItem top : menuItemRepo.findByMenuIdAndParentIsNullOrderBySortOrderAsc(menuId)) {
            collectWithDescendants(top, doomed);
        }
        for (int i = doomed.size() - 1; i >= 0; i--) {
            menuItemRepo.delete(doomed.get(i));
        }
        menuRepo.deleteById(menuId);
        return true;
    }

    private String defaultUrlForSlug(String slug) {
        if (slug == null || slug.isBlank() || slug.equals("home")) {
            return "/";
        }
        String clean = slug;
        while (clean.startsWith("/")) {
            clean = clean.substring(1);
        }
        return "/" + clean;
    }

    private MenuResponse toResponse(Menu menu) {

        // Build a tree: top-level items with nested children attached.
        List<MenuItemResponse> items = menu.getItems().stream()
                .filter(item -> item.getParent() == null)
                .sorted((a, b) -> Integer.compare(
                        a.getSortOrder() == null ? 0 : a.getSortOrder(),
                        b.getSortOrder() == null ? 0 : b.getSortOrder()))
                .map(this::toItemResponse)
                .toList();

        return new MenuResponse(
                menu.getId(),
                menu.getName(),
                menu.getNameFa(),
                menu.getSlug(),
                menu.getUrl() != null ? menu.getUrl() : defaultUrlForSlug(menu.getSlug()),
                items
        );
    }

    private MenuItemResponse toItemResponse(MenuItem item) {
        List<MenuItemResponse> children = item.getChildren() == null ? List.of() :
                item.getChildren().stream()
                        .sorted((a, b) -> Integer.compare(
                                a.getSortOrder() == null ? 0 : a.getSortOrder(),
                                b.getSortOrder() == null ? 0 : b.getSortOrder()))
                        .map(this::toItemResponse)
                        .toList();
        Long parentId = item.getParent() == null ? null : item.getParent().getId();
        return new MenuItemResponse(
                item.getId(),
                item.getTitle(),
                item.getTitleFa(),
                item.getUrl(),
                item.getSortOrder(),
                parentId,
                children,
                MenuItemService.isProductItem(item)
        );
    }

    private void collectWithDescendants(MenuItem item, List<MenuItem> out) {
        out.add(item);
        if (item.getChildren() != null) {
            for (MenuItem child : item.getChildren()) {
                collectWithDescendants(child, out);
            }
        }
    }

}
