package ir.linuxian.second.service;

import ir.linuxian.second.dto.MenuItemResponse;
import ir.linuxian.second.entities.menu.Menu;
import ir.linuxian.second.entities.menu.MenuItem;
import ir.linuxian.second.entities.page.Page;
import ir.linuxian.second.repos.MenuItemRepo;
import ir.linuxian.second.repos.MenuRepo;
import ir.linuxian.second.repos.PageRepo;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MenuItemService {

    private final MenuItemRepo menuItemRepo;
    private final MenuRepo menuRepo;
    private final PageRepo pageRepository;

    public MenuItemService(MenuItemRepo menuItemRepo, MenuRepo menuRepo, PageRepo pageRepository) {
        this.menuItemRepo = menuItemRepo;
        this.menuRepo = menuRepo;
        this.pageRepository = pageRepository;
    }

    public List<MenuItemResponse> getItemsForMenu(Long menuId) {

      return   menuItemRepo.findByMenuIdAndParentIsNullOrderBySortOrderAsc(menuId)
              .stream()
              .map(this::toResponse)
              .toList();

    }

    public MenuItemResponse getMenuItemById(Long menuId) {
        MenuItem menuItem = menuItemRepo.findById(menuId).orElseThrow(()->new RuntimeException("menuitem not found! "+menuId));
    return toResponse(menuItem);
    }

    public List<MenuItemResponse> getChildren(Long parentId) {
        return menuItemRepo.findByParentIdOrderBySortOrderAsc(parentId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public MenuItemResponse createItem(
            Long menuId,
            MenuItem item,
            Long parentId
    ) {
        Menu menu = menuRepo.findById(menuId)
                .orElseThrow(() ->
                        new RuntimeException("Menu not found: " + menuId)
                );

        item.setMenu(menu);

        if (parentId != null) {

            MenuItem parent = menuItemRepo.findById(parentId)
                    .orElseThrow(() ->
                            new RuntimeException(
                                    "Parent menu item not found: " + parentId
                            )
                    );

            item.setParent(parent);

            item.setUrl(parent.getUrl()+item.getUrl());

            int childCount =
                    menuItemRepo.countByParentId(parentId);

            item.setSortOrder(childCount + 1);

        } else {

            int itemCount =
                    menuItemRepo
                            .countByMenuIdAndParentIsNull(menuId);

            item.setSortOrder(itemCount + 1);
        }

        // Save the menu item
        MenuItem savedItem =
                menuItemRepo.save(item);


        // Create the corresponding raw page
        Page page = new Page();

        page.setTitle(savedItem.getTitle());

        page.setSlug(
                createSlugFromUrl(savedItem.getUrl())
        );

        page.setContent("{}");

        pageRepository.save(page);


        return toResponse(savedItem);
    }
    public MenuItem updateMenuItem(Long menuId, MenuItem menuItem) {

        MenuItem oldMenuItem = menuItemRepo.getMenuItemsById(menuId);

        oldMenuItem.setTitle(menuItem.getTitle());
        oldMenuItem.setUrl(menuItem.getUrl());
        oldMenuItem.setSortOrder(menuItem.getSortOrder());


        return menuItemRepo.save(menuItem);

    }

    @Transactional
    public void deleteItem(Long id) {

        MenuItem item = menuItemRepo.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Menu item not found: " + id)
                );

        deleteItemAndPage(item);
    }


    private void deleteItemAndPage(MenuItem item) {

        // First delete children
        for (MenuItem child : item.getChildren()) {
            deleteItemAndPage(child);
        }

        // Delete associated page
        String slug = createSlugFromUrl(item.getUrl());

        pageRepository.findBySlug(slug)
                .ifPresent(pageRepository::delete);

        // Delete menu item
        menuItemRepo.delete(item);
    }



    private void deleteChildren(MenuItem item) {

        List<MenuItem> children = item.getChildren();

        for (MenuItem child : children) {
            deleteChildren(child);
            menuItemRepo.delete(child);
        }
    }
    private MenuItemResponse toResponse(MenuItem item) {

        List<MenuItemResponse> children = item.getChildren()
                .stream()
                .map(this::toResponse)
                .toList();

        return new MenuItemResponse(
                item.getId(),
                item.getTitle(),
                item.getUrl(),
                item.getSortOrder(),
                children
        );
    }

    private String createSlugFromUrl(String url) {

        if (url == null || url.isBlank()) {
            throw new RuntimeException("Menu item URL cannot be empty");
        }

        String slug = url;

        // Remove leading /
        while (slug.startsWith("/")) {
            slug = slug.substring(1);
        }

        // Remove trailing /
        while (slug.endsWith("/")) {
            slug = slug.substring(0, slug.length() - 1);
        }

        return slug;
    }

}
