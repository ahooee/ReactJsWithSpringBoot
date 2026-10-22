package ir.linuxian.second.service;

import ir.linuxian.second.dto.MenuItemResponse;
import ir.linuxian.second.entities.menu.Menu;
import ir.linuxian.second.entities.menu.MenuItem;
import ir.linuxian.second.entities.product.Product;
import ir.linuxian.second.repos.MenuItemRepo;
import ir.linuxian.second.repos.MenuRepo;
import ir.linuxian.second.repos.PageRepo;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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

    /**
     * A menu item linking to a single product page ({@code /shop/{slug}}).
     * Product pages are leaf pages: they can sit anywhere in a menu but
     * can never be a parent (have submenus).
     */
    public static boolean isProductItem(MenuItem item) {
        if (item == null) return false;
        return isProductUrl(item.getUrl());
    }

    public static boolean isProductUrl(String url) {
        if (url == null || url.isBlank()) return false;
        String path = url.split("[?#]", 2)[0];
        while (path.endsWith("/") && path.length() > 1) {
            path = path.substring(0, path.length() - 1);
        }
        if (!path.startsWith("/shop/")) return false;
        String rest = path.substring("/shop/".length());
        return !rest.isEmpty() && !rest.contains("/");
    }

    private static void requireParentable(MenuItem parent) {
        if (isProductItem(parent)) {
            throw new RuntimeException("A product link (\"" + parent.getTitle() + "\") cannot have submenus");
        }
    }

    @Transactional
    public MenuItemResponse createItem(
            Long menuId,
            MenuItem item
    ) {
        Menu menu = menuRepo.findById(menuId)
                .orElseThrow(() ->
                        new RuntimeException("Menu not found: " + menuId)
                );

        item.setMenu(menu);

        // Optional nesting: { "parent": { "id": N } } attaches under a sibling.
        if (item.getParent() != null && item.getParent().getId() != null) {
            MenuItem parent = menuItemRepo.findById(item.getParent().getId())
                    .orElseThrow(() -> new RuntimeException("Parent menu item not found: " + item.getParent().getId()));
            if (parent.getMenu() == null || !parent.getMenu().getId().equals(menuId)) {
                throw new RuntimeException("Parent item belongs to another menu");
            }
            if (parent.getId().equals(item.getId())) {
                throw new RuntimeException("A menu item cannot be its own parent");
            }
            requireParentable(parent);
            item.setParent(parent);
            item.setSortOrder(menuItemRepo.countByParentId(parent.getId()) + 1);
        } else {
            item.setParent(null);
            int itemCount =
                    menuItemRepo.countByMenuId(menuId);

            item.setSortOrder(itemCount + 1);
        }

        // Save the menu item
        MenuItem savedItem =
                menuItemRepo.save(item);


        // Create the corresponding raw page (filtered views like /shop?type=ebook are not pages).
        if (!isPageableUrl(savedItem.getUrl())) {
            return toResponse(savedItem);
        }
        pageRepository.findBySlug(createSlugFromUrl(savedItem.getUrl()))
                .orElseGet(() -> {
                    ir.linuxian.second.entities.page.Page page = new ir.linuxian.second.entities.page.Page();

                    page.setTitle(savedItem.getTitle());

                    page.setSlug(
                            createSlugFromUrl(savedItem.getUrl())
                    );

                    page.setContent("{}");

                    return pageRepository.save(page);
                });


        return toResponse(savedItem);
    }
    public MenuItemResponse updateMenuItem(Long menuId, MenuItem menuItem) {

        MenuItem oldMenuItem = menuItemRepo.getMenuItemsById(menuId);

        // Partial updates allowed: only non-blank/non-null fields are applied,
        // so reordering (sortOrder-only) never wipes title/url.
        if (menuItem.getTitle() != null && !menuItem.getTitle().isBlank()) {
            oldMenuItem.setTitle(menuItem.getTitle().trim());
        }
        if (menuItem.getTitleFa() != null) {
            oldMenuItem.setTitleFa(menuItem.getTitleFa());
        }
        if (menuItem.getUrl() != null && !menuItem.getUrl().isBlank()) {
            String newUrl = menuItem.getUrl().trim();
            if (isProductUrl(newUrl) && !isProductUrl(oldMenuItem.getUrl())
                    && !menuItemRepo.findByParentIdOrderBySortOrderAsc(oldMenuItem.getId()).isEmpty()) {
                throw new RuntimeException("\"" + oldMenuItem.getTitle() + "\" has submenus, so its link cannot point to a product page");
            }
            oldMenuItem.setUrl(newUrl);
        }
        if (menuItem.getSortOrder() != null) {
            oldMenuItem.setSortOrder(menuItem.getSortOrder());
        }

        return toResponse(menuItemRepo.save(oldMenuItem));

    }

    /**
     * Move an item within/between hierarchy levels of the same menu.
     * parentId null = top level. Appends at the end of the new sibling group.
     */
    @Transactional
    public MenuItemResponse moveItem(Long id, Long parentId) {
        MenuItem item = menuItemRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Menu item not found: " + id));
        Long menuId = item.getMenu().getId();
        if (parentId == null) {
            item.setParent(null);
            item.setSortOrder(menuItemRepo.countByMenuId(menuId) + 1);
        } else {
            MenuItem parent = menuItemRepo.findById(parentId)
                    .orElseThrow(() -> new RuntimeException("Parent menu item not found: " + parentId));
            if (parent.getMenu() == null || !parent.getMenu().getId().equals(menuId)) {
                throw new RuntimeException("Parent item belongs to another menu");
            }
            // Cycle guard: the new parent must not be the item or its own descendant.
            MenuItem cursor = parent;
            while (cursor != null) {
                if (cursor.getId().equals(id)) {
                    throw new RuntimeException("A menu item cannot be moved under itself or its own submenu");
                }
                cursor = cursor.getParent();
            }
            requireParentable(parent);
            item.setParent(parent);
            item.setSortOrder(menuItemRepo.countByParentId(parentId) + 1);
        }
        return toResponse(menuItemRepo.save(item));
    }

    @Transactional
    public void deleteItem(Long id) {

        MenuItem item = menuItemRepo.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Menu item not found: " + id)
                );

        // Collect the item plus all descendants, then delete deepest-first
        // so nested (parent_id) rows never violate the FK.
        java.util.List<MenuItem> doomed = new java.util.ArrayList<>();
        collectWithDescendants(item, doomed);

        // Delete associated auto-created pages
        for (MenuItem doomedItem : doomed) {
            try {
                if (isPageableUrl(doomedItem.getUrl())) {
                    String slug = createSlugFromUrl(doomedItem.getUrl());
                    pageRepository.findBySlug(slug).ifPresent(pageRepository::delete);
                }
            } catch (RuntimeException ignored) {
                // blank URL — nothing to clean up
            }
        }

        for (int i = doomed.size() - 1; i >= 0; i--) {
            menuItemRepo.delete(doomed.get(i));
        }
    }

    // ---------- automatic product-link placement (Shop -> Books -> E-books/Paper-books) ----------

    public static String productItemUrl(String productSlug) {
        return "/shop/" + productSlug;
    }

    public static String autoProductTitle(String productName) {
        return "Buy " + (productName == null ? "" : productName);
    }

    public static String autoProductTitleFa(String productName) {
        return "خرید " + (productName == null ? "" : productName);
    }

    /** The Books submenu that owns products of the given book type, or null. */
    private MenuItem findBooksSubmenu(Long shopId, String bookType) {
        String want = "PAPER".equals(ProductService.normalizeBookType(bookType))
                ? "/books/paper-books" : "/books/ebooks";
        List<MenuItem> found = menuItemRepo.findAllByMenuIdAndUrl(shopId, want);
        return found.isEmpty() ? null : found.get(0);
    }

    /**
     * Normalize one product's Shop entry: exactly one item with its URL,
     * placed directly under the matching Books submenu (top level if the
     * submenu is missing). Returns the {created, moved, removed} delta.
     */
    @Transactional
    public Map<String, Integer> placeShopProductItem(Product product) {
        Map<String, Integer> delta = new LinkedHashMap<>();
        delta.put("created", 0);
        delta.put("moved", 0);
        delta.put("removed", 0);
        if (product == null || product.getSlug() == null || product.getSlug().isBlank()) {
            return delta;
        }
        Menu shop = menuRepo.findBySlug("shop").orElse(null);
        if (shop == null) {
            return delta;
        }
        String url = productItemUrl(product.getSlug());
        List<MenuItem> matches = menuItemRepo.findAllByMenuIdAndUrl(shop.getId(), url);
        matches.sort(Comparator.comparing(MenuItem::getId));
        MenuItem submenu = findBooksSubmenu(shop.getId(), product.getBookType());
        Long wantParent = submenu == null ? null : submenu.getId();

        MenuItem keep = null;
        for (MenuItem m : matches) {
            if (keep == null) {
                keep = m;
            } else {
                deleteItem(m.getId());
                delta.put("removed", delta.get("removed") + 1);
            }
        }
        if (keep == null) {
            MenuItem item = new MenuItem(autoProductTitle(product.getName()), url, 0);
            item.setTitleFa(autoProductTitleFa(product.getName()));
            if (submenu != null) {
                item.setParent(submenu);
            }
            createItem(shop.getId(), item);
            delta.put("created", 1);
            return delta;
        }
        Long haveParent = keep.getParent() == null ? null : keep.getParent().getId();
        if (!Objects.equals(wantParent, haveParent)) {
            moveItem(keep.getId(), wantParent);
            delta.put("moved", 1);
        }
        return delta;
    }

    /** Called after a product is created: adopt or create its Shop entry. */
    @Transactional
    public void syncProductOnCreate(Product product) {
        placeShopProductItem(product);
    }

    /**
     * Called after a product is updated. Renames/relinks matching Shop items
     * (titles only when they still match the auto pattern, so custom titles
     * are never clobbered) and normalizes placement.
     */
    @Transactional
    public void syncProductOnUpdate(String oldSlug, String oldName, Product product) {
        if (product == null) {
            return;
        }
        Menu shop = menuRepo.findBySlug("shop").orElse(null);
        if (shop != null && oldSlug != null && !oldSlug.isBlank()) {
            String newUrl = productItemUrl(product.getSlug());
            for (MenuItem m : menuItemRepo.findAllByMenuIdAndUrl(shop.getId(), productItemUrl(oldSlug))) {
                boolean changed = false;
                if (!newUrl.equals(m.getUrl())) {
                    m.setUrl(newUrl);
                    changed = true;
                }
                if (oldName != null && product.getName() != null) {
                    if (autoProductTitle(oldName).equals(m.getTitle())) {
                        m.setTitle(autoProductTitle(product.getName()));
                        changed = true;
                    }
                    if (autoProductTitleFa(oldName).equals(m.getTitleFa())) {
                        m.setTitleFa(autoProductTitleFa(product.getName()));
                        changed = true;
                    }
                }
                if (changed) {
                    menuItemRepo.save(m);
                }
            }
        }
        placeShopProductItem(product);
    }

    /** Called when a product is deleted: remove its Shop entries. */
    @Transactional
    public void syncProductOnDelete(String productSlug) {
        if (productSlug == null || productSlug.isBlank()) {
            return;
        }
        Menu shop = menuRepo.findBySlug("shop").orElse(null);
        if (shop == null) {
            return;
        }
        for (MenuItem m : menuItemRepo.findAllByMenuIdAndUrl(shop.getId(), productItemUrl(productSlug))) {
            deleteItem(m.getId());
        }
    }

    /**
     * Full reconcile over a catalog: every product gets exactly one Shop
     * entry under its type submenu. Returns {created, moved, removed}.
     */
    @Transactional
    public Map<String, Integer> syncAllProducts(List<Product> products) {
        Map<String, Integer> total = new LinkedHashMap<>();
        total.put("created", 0);
        total.put("moved", 0);
        total.put("removed", 0);
        if (products == null) {
            return total;
        }
        for (Product p : products) {
            Map<String, Integer> delta = placeShopProductItem(p);
            total.put("created", total.get("created") + delta.get("created"));
            total.put("moved", total.get("moved") + delta.get("moved"));
            total.put("removed", total.get("removed") + delta.get("removed"));
        }
        return total;
    }

    private void collectWithDescendants(MenuItem item, java.util.List<MenuItem> out) {
        out.add(item);
        for (MenuItem child : menuItemRepo.findByParentIdOrderBySortOrderAsc(item.getId())) {
            collectWithDescendants(child, out);
        }
    }

    private MenuItemResponse toResponse(MenuItem item) {

        java.util.List<MenuItemResponse> children = menuItemRepo.findByParentIdOrderBySortOrderAsc(item.getId())
                .stream()
                .map(this::toResponse)
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
                isProductItem(item)
        );
    }

    /** Filtered views (/shop?type=ebook) and anchors are not CMS pages. */
    private boolean isPageableUrl(String url) {
        if (url == null || url.isBlank()) return false;
        return !(url.contains("?") || url.contains("#"));
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
