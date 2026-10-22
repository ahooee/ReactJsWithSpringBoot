package ir.linuxian.second;

import ir.linuxian.second.entities.Role;
import ir.linuxian.second.entities.RoleName;
import ir.linuxian.second.entities.User;
import ir.linuxian.second.entities.menu.Menu;
import ir.linuxian.second.entities.menu.MenuItem;
import ir.linuxian.second.entities.page.Page;
import ir.linuxian.second.entities.post.Post;
import ir.linuxian.second.entities.product.Product;
import ir.linuxian.second.entities.settings.SiteSetting;
import ir.linuxian.second.repos.*;
import ir.linuxian.second.service.CryptoKeyStore;
import ir.linuxian.second.service.MenuItemService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import java.util.List;

/**
 * First-boot and every-boot initialization: contact-encryption secret,
 * roles/users, settings, pages, posts, products, menus and data repairs.
 * Everything here is idempotent — re-running on an existing database is a
 * no-op except for targeted repairs/backfills that only act when needed.
 */
@Component
public class DataInitializer implements CommandLineRunner {

    Logger logger = LoggerFactory.getLogger(DataInitializer.class);
    private final UserRepo userRepo;
    private final RoleRepo roleRepo;
    private final MenuRepo menuRepo;
    private final MenuItemRepo menuItemRepo;
    private final PageRepo pageRepo;
    private final PostRepo postRepo;
    private final ProductRepo productRepo;
    private final BasketItemRepo basketItemRepo;
    private final ProductViewRepo productViewRepo;
    private final SiteSettingRepo settingRepo;
    private final MenuItemService menuItemService;
    private final PiiEncryptionMigration piiEncryptionMigration;

    public DataInitializer(UserRepo userRepo, RoleRepo roleRepo, MenuRepo menuRepo,
                           MenuItemRepo menuItemRepo, PageRepo pageRepo, PostRepo postRepo,
                           ProductRepo productRepo, BasketItemRepo basketItemRepo,
                           ProductViewRepo productViewRepo, SiteSettingRepo settingRepo,
                           MenuItemService menuItemService,
                           PiiEncryptionMigration piiEncryptionMigration) {
        this.userRepo = userRepo;
        this.roleRepo = roleRepo;
        this.menuRepo = menuRepo;
        this.menuItemRepo = menuItemRepo;
        this.pageRepo = pageRepo;
        this.postRepo = postRepo;
        this.productRepo = productRepo;
        this.basketItemRepo = basketItemRepo;
        this.productViewRepo = productViewRepo;
        this.settingRepo = settingRepo;
        this.menuItemService = menuItemService;
        this.piiEncryptionMigration = piiEncryptionMigration;
    }

    @Override
    public void run(String... args) throws Exception {
        // First: contact encryption must have a stable secret before any
        // encrypted column (user email/phone, verification contacts) is
        // touched. Generates ./.app-crypto-key on first boot when no
        // APP_CRYPTO_KEY env var is set.
        CryptoKeyStore.ensureKeyFile();
        try {
            seedRolesAndUsers();
        } catch (Exception e) {
            logger.error("Seeding roles/users failed (app continues): {}", e.getMessage());
        }
        try {
            seedSettings();
            seedPages();
            seedPosts();
            // The catalog is backed by the books table only; drop the old
            // demo products (and their basket/view/menu references) once.
            removeDemoProducts();
            seedMenu();
        } catch (Exception e) {
            logger.error("Seeding content failed (app continues): {}", e.getMessage());
        }
        try {
            fixBlankSlugs();
        } catch (Exception e) {
            logger.error("Slug repair failed (app continues): {}", e.getMessage());
        }
        try {
            piiEncryptionMigration.migrate();
        } catch (Exception e) {
            logger.error("PII encryption migration failed (app continues): {}", e.getMessage());
        }
    }

    // Repairs rows created before slugify had a non-latin fallback
    // (e.g. Persian titles that produced an empty slug and dead links).
    private void fixBlankSlugs() {
        for (Post p : postRepo.findAll()) {
            if (p.getSlug() == null || p.getSlug().isBlank()) {
                p.setSlug("post-" + p.getId() + "-" + System.currentTimeMillis());
                postRepo.save(p);
                logger.info("Repaired blank post slug: id={}", p.getId());
            }
        }
        for (Product p : productRepo.findAll()) {
            if (p.getSlug() == null || p.getSlug().isBlank()) {
                p.setSlug("product-" + p.getId() + "-" + System.currentTimeMillis());
                productRepo.save(p);
                logger.info("Repaired blank product slug: id={}", p.getId());
            }
        }
    }

    private void seedRolesAndUsers() {
        Role admin = findOrCreateRole(RoleName.ROLE_ADMIN);
        Role user = findOrCreateRole(RoleName.ROLE_USER);
        findOrCreateRole(RoleName.ROLE_MODERATOR);

        if (userRepo.findByUsername("mohammad").isEmpty()) {
            userRepo.save(new User("mohammad",
                    new BCryptPasswordEncoder().encode("linuxian"), "admin", List.of(admin)));
            logger.info("Seeded admin user: mohammad");
        }
        if (userRepo.findByUsername("demo").isEmpty()) {
            userRepo.save(new User("demo",
                    new BCryptPasswordEncoder().encode("demo1234"), "user", List.of(user)));
            logger.info("Seeded demo user: demo / demo1234");
        }
    }

    private Role findOrCreateRole(RoleName name) {
        return roleRepo.findByRoleName(name).orElseGet(() -> {
            try {
                return roleRepo.save(new Role(name));
            } catch (org.springframework.dao.DataIntegrityViolationException e) {
                // Another instance inserted the role concurrently (double start):
                // re-read the winner instead of failing startup.
                logger.warn("Role {} was created concurrently, reusing it.", name);
                return roleRepo.findByRoleName(name).orElseThrow(() -> e);
            }
        });
    }

    private void seedSettings() {
        setDefault("siteName", "My Store");
        setDefault("siteTagline", "A WordPress-like CMS built with Spring Boot + React");
        setDefault("defaultLanguage", "fa");
        setDefault("theme", "default");
        setDefault("colorScheme", "light");
        setDefault("accentColor", "#aa3bff");
        setDefault("heroTitle", "Welcome to My Store");
        setDefault("heroSubtitle", "Posts, products, pages and menus — all manageable from the admin panel.");
        setDefault("heroButtonText", "Shop now");
        setDefault("heroButtonUrl", "/shop");
    }

    private void setDefault(String key, String value) {
        if (settingRepo.findById(key).isEmpty()) {
            settingRepo.save(new SiteSetting(key, value));
        }
    }

    private void seedPages() {
        if (pageRepo.findBySlug("home").isEmpty()) {
            Page home = new Page();
            home.setTitle("Home");
            home.setSlug("home");
            home.setContent("{\"hero\":{\"title\":\"Welcome to My Store\",\"subtitle\":\"Manage posts, products, pages and menus from admin\",\"button\":{\"text\":\"Shop now\",\"url\":\"/shop\"}},\"featuredItems\":{\"title\":\"Featured\",\"items\":[]},\"about\":{\"title\":\"About us\",\"text\":\"This is a demo CMS.\"},\"callToAction\":{\"title\":\"Get started\",\"text\":\"Sign up and explore the admin panel.\"}}");
            pageRepo.save(home);
        }
        if (pageRepo.findBySlug("about").isEmpty()) {
            Page about = new Page();
            about.setTitle("About");
            about.setSlug("about");
            about.setContent("{\"title\":\"About\",\"text\":\"This page is editable from Admin > Pages.\"}");
            pageRepo.save(about);
        }
    }

    private void seedMenu() {
        migrateLegacyMainMenu();

        Menu home = findOrCreateMenu("Home", "خانه", "home", "/");
        Menu shop = findOrCreateMenu("Shop", "فروشگاه", "shop", "/shop");
        Menu blog = findOrCreateMenu("Blog", "وبلاگ", "blog", "/blog");

        // Sample children so Blog renders a dropdown (admin can extend/remove them).
        // Product links are NOT seeded here: the sync below places every
        // product under its Books submenu (Shop -> Books -> E-books/Paper books).
        postRepo.findAll().stream().limit(2).forEach(p -> {
            addMenuItemIfMissing(blog, p.getTitle(), p.getTitle(), "/blog/" + p.getSlug());
        });
        addMenuItemIfMissing(shop, "All products", "همه محصولات", "/shop");
        addMenuItemIfMissing(blog, "Latest posts", "آخرین نوشته‌ها", "/blog");

        // Shop > Books > { E-books, Paper books } nested dropdown.
        // Dedicated /books pages (migrated from the old /shop?type= filter URLs).
        migrateBookMenuUrls(shop);
        MenuItem books = addMenuItemIfMissingReturning(shop, "Books", "کتاب‌ها", "/books");
        addChildItemIfMissing(shop, books, "E-books", "کتاب الکترونیکی", "/books/ebooks");
        addChildItemIfMissing(shop, books, "Paper books", "کتاب چاپی", "/books/paper-books");

        // Deterministic backfill: fill Persian titles for ALL existing
        // items of these menus (creation above only visits a few products
        // and findAll() ordering varies between boots).
        backfillShopAndBlogFa(shop, blog);
        repairProductParents();
        // Every product gets exactly one Shop entry under its type submenu.
        java.util.Map<String, Integer> synced = menuItemService.syncAllProducts(productRepo.findAll());
        if (synced.getOrDefault("created", 0) + synced.getOrDefault("moved", 0) + synced.getOrDefault("removed", 0) > 0) {
            logger.info("Synced Shop product links: {}", synced);
        }
    }

    private void backfillShopAndBlogFa(Menu shop, Menu blog) {
        for (MenuItem item : menuItemRepo.findByMenuIdOrderBySortOrderAsc(shop.getId())) {
            if (item.getTitleFa() == null || item.getTitleFa().isBlank()) {
                String fa = null;
                if ("/shop".equals(item.getUrl())) {
                    fa = "همه محصولات";
                } else if (item.getUrl() != null && item.getUrl().startsWith("/shop/")) {
                    String slug = item.getUrl().substring("/shop/".length());
                    fa = productRepo.findBySlug(slug).map(p -> "خرید " + p.getName()).orElse(null);
                }
                if (fa != null) {
                    item.setTitleFa(fa);
                    menuItemRepo.save(item);
                }
            }
        }
        for (MenuItem item : menuItemRepo.findByMenuIdOrderBySortOrderAsc(blog.getId())) {
            if (item.getTitleFa() == null || item.getTitleFa().isBlank()) {
                String fa = null;
                if ("/blog".equals(item.getUrl())) {
                    fa = "آخرین نوشته‌ها";
                } else if (item.getUrl() != null && item.getUrl().startsWith("/blog/")) {
                    String slug = item.getUrl().substring("/blog/".length());
                    fa = postRepo.findBySlug(slug).map(p -> p.getTitle()).orElse(null);
                }
                if (fa != null) {
                    item.setTitleFa(fa);
                    menuItemRepo.save(item);
                }
            }
        }
    }

    private Menu findOrCreateMenu(String name, String nameFa, String slug, String url) {
        Menu existing = menuRepo.findBySlug(slug).orElse(null);
        if (existing != null) {
            if ((existing.getNameFa() == null || existing.getNameFa().isBlank())
                    && nameFa != null && !nameFa.isBlank()) {
                existing.setNameFa(nameFa);
                existing = menuRepo.save(existing);
            }
            return existing;
        }
        Menu menu = new Menu(name, slug);
        menu.setNameFa(nameFa);
        menu.setUrl(url);
        return menuRepo.save(menu);
    }

    private void addMenuItemIfMissing(Menu menu, String title, String titleFa, String url) {
        addMenuItemIfMissingReturning(menu, title, titleFa, url);
    }

    private MenuItem addMenuItemIfMissingReturning(Menu menu, String title, String titleFa, String url) {
        MenuItem existing = menuItemRepo.findAllByMenuIdAndUrl(menu.getId(), url).stream().findFirst().orElse(null);
        if (existing != null) {
            if ((existing.getTitleFa() == null || existing.getTitleFa().isBlank())
                    && titleFa != null && !titleFa.isBlank()) {
                existing.setTitleFa(titleFa);
                existing = menuItemRepo.save(existing);
            }
            return existing;
        }
        MenuItem item = new MenuItem(title, url, 0);
        item.setTitleFa(titleFa);
        menuItemService.createItem(menu.getId(), item);
        return menuItemRepo.findAllByMenuIdAndUrl(menu.getId(), url).stream().findFirst().orElse(item);
    }

    private void addChildItemIfMissing(Menu menu, MenuItem parent, String title, String titleFa, String url) {
        boolean exists = menuItemRepo.findByParentIdOrderBySortOrderAsc(parent.getId())
                .stream().anyMatch(c -> url.equals(c.getUrl()));
        if (exists) {
            for (MenuItem child : menuItemRepo.findByParentIdOrderBySortOrderAsc(parent.getId())) {
                if (url.equals(child.getUrl())
                        && (child.getTitleFa() == null || child.getTitleFa().isBlank())
                        && titleFa != null && !titleFa.isBlank()) {
                    child.setTitleFa(titleFa);
                    menuItemRepo.save(child);
                }
            }
            return;
        }
        MenuItem item = new MenuItem(title, url, 0);
        item.setTitleFa(titleFa);
        item.setParent(parent);
        menuItemService.createItem(menu.getId(), item);
    }

    /**
     * The Books submenu used to link to /shop?type=... filters; it now has
     * dedicated /books pages. Rewrite those URLs in place (no duplicates).
     */
    private void migrateBookMenuUrls(Menu shop) {
        java.util.Map<String, String> moves = java.util.Map.of(
                "/shop?type=all", "/books",
                "/shop?type=ebook", "/books/ebooks",
                "/shop?type=paper", "/books/paper-books");
        for (MenuItem item : menuItemRepo.findByMenuIdOrderBySortOrderAsc(shop.getId())) {
            String from = item.getUrl();
            String target = moves.get(from);
            if (target != null && menuItemRepo.findAllByMenuIdAndUrl(shop.getId(), target).isEmpty()) {
                item.setUrl(target);
                menuItemRepo.save(item);
                logger.info("Migrated book menu URL {} -> {}", from, target);
            }
        }
    }

    /**
     * Products are leaf pages and cannot be parents. If an older database has
     * submenus nested under a product link, lift them back to the top level.
     * Only touches data while violations exist.
     */
    private void repairProductParents() {
        for (Menu menu : menuRepo.findAll()) {
            for (MenuItem item : menuItemRepo.findByMenuIdOrderBySortOrderAsc(menu.getId())) {
                if (MenuItemService.isProductItem(item)
                        && !menuItemRepo.findByParentIdOrderBySortOrderAsc(item.getId()).isEmpty()) {
                    for (MenuItem child : menuItemRepo.findByParentIdOrderBySortOrderAsc(item.getId())) {
                        menuItemService.moveItem(child.getId(), null);
                        logger.info("Moved \"{}\" to top level: a product cannot have submenus", child.getTitle());
                    }
                }
            }
        }
    }

    private void migrateLegacyMainMenu() {
        menuRepo.findBySlug("main").ifPresent(main -> {
            try {
                detachLegacyChildren(main.getId());
                for (MenuItem item : menuItemRepo.findByMenuIdOrderBySortOrderAsc(main.getId())) {
                    deletePlaceholderPage(item.getUrl());
                }
                menuRepo.delete(main);
                logger.info("Migrated legacy 'main' menu to Menu → MenuItem structure");
            } catch (Exception e) {
                logger.error("Legacy menu migration failed (app continues): {}", e.getMessage());
            }
        });
    }

    private void detachLegacyChildren(Long menuId) {
        try {
            menuItemRepo.clearLegacyParents(menuId);
        } catch (Exception e) {
            logger.warn("Legacy parent_id cleanup skipped (column may already be gone): {}", e.getMessage());
        }
    }

    private void deletePlaceholderPage(String url) {
        if (url == null) {
            return;
        }
        String slug = url;
        while (slug.startsWith("/")) {
            slug = slug.substring(1);
        }
        while (slug.endsWith("/")) {
            slug = slug.substring(0, slug.length() - 1);
        }
        if (slug.isBlank()) {
            return;
        }
        pageRepo.findBySlug(slug).ifPresent(page -> {
            String content = page.getContent();
            if (content == null || content.isBlank() || content.trim().equals("{}")) {
                pageRepo.delete(page);
            }
        });
    }

    private void seedPosts() {
        if (postRepo.count() == 0) {
            for (int i = 1; i <= 3; i++) {
                Post p = new Post();
                p.setTitle("Hello post " + i);
                p.setSlug("hello-post-" + i);
                p.setExcerpt("Short excerpt for post " + i);
                p.setContent("This is the full content of demo post " + i + ". Edit me from Admin > Posts.");
                p.setStatus("PUBLISHED");
                p.setAuthorUsername("mohammad");
                postRepo.save(p);
            }
        }
    }

    /**
     * Removes the seeded demo catalog. The storefront reads the books table
     * instead, so these rows only produced dead product links. Idempotent:
     * does nothing once the demo rows are gone.
     */
    private void removeDemoProducts() {
        List<Product> demos = productRepo.findAll().stream()
                .filter(p -> p.getSlug() != null && p.getSlug().startsWith("demo-product-"))
                .toList();
        if (demos.isEmpty()) {
            return;
        }
        for (Product p : demos) {
            menuItemService.syncProductOnDelete(p.getSlug());
            basketItemRepo.deleteByProductId(p.getId());
            productViewRepo.deleteByProduct(p.getId());
            productRepo.delete(p);
        }
        logger.info("Removed {} demo product(s); the catalog uses the books table only", demos.size());
    }
}
