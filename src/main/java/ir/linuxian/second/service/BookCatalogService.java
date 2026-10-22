package ir.linuxian.second.service;

import ir.linuxian.second.dto.BookCategoryView;
import ir.linuxian.second.dto.BookView;
import ir.linuxian.second.entities.book.Book;
import ir.linuxian.second.entities.book.BookCategoryName;
import ir.linuxian.second.entities.product.Product;
import ir.linuxian.second.repos.BookCategoryNameRepo;
import ir.linuxian.second.repos.BookRepo;
import ir.linuxian.second.repos.ProductRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Read-only catalog backed exclusively by the {@code books} table.
 *
 * <p>Only rows with {@code downloaded = true} are exposed. Categories are
 * derived from the downloaded rows' {@code categories} column, and covers
 * are resolved from the {@code cover}/{@code cover_path} columns under the
 * downloader's library root.</p>
 */
@Service
public class BookCatalogService {

    private static final Logger log = LoggerFactory.getLogger(BookCatalogService.class);
    private static final String DOWNLOADS_PREFIX = "downloads/";
    private static final Pattern YEAR = Pattern.compile("\\b(1[5-9]\\d{2}|20\\d{2})\\b");

    private final BookRepo bookRepo;
    private final ProductRepo productRepo;
    private final SettingService settingService;
    private final BookCategoryNameRepo categoryNameRepo;
    private final Path libraryRoot;

    public BookCatalogService(BookRepo bookRepo, ProductRepo productRepo,
                              SettingService settingService, BookCategoryNameRepo categoryNameRepo,
                              @Value("${app.books.library-root:../zlibrary/zlibrary/downloads}") String libraryRoot) {
        this.bookRepo = bookRepo;
        this.productRepo = productRepo;
        this.settingService = settingService;
        this.categoryNameRepo = categoryNameRepo;
        this.libraryRoot = Paths.get(libraryRoot).toAbsolutePath().normalize();
        if (!Files.isDirectory(this.libraryRoot)) {
            log.warn("Books library root does not exist: {} (set app.books.library-root or BOOKS_LIBRARY_ROOT)",
                    this.libraryRoot);
        }
    }

    /** All downloaded books, ordered by title. Translations and prices are
     * fetched once for the whole list so the cards show both without N+1. */
    @Transactional(readOnly = true)
    public List<BookView> findDownloaded() {
        List<Book> rows = bookRepo.findByDownloadedTrueOrderByTitleAsc();
        Map<String, String> nameBySlug = categoryNames();
        Map<UUID, Long> priceByBook = priceMapFor(rows);
        return rows.stream()
                .map(book -> toView(book, nameBySlug, priceByBook))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<BookView> findDownloaded(UUID id) {
        return findDownloadedBook(id)
                .map(book -> toView(book, categoryNames(), priceMapFor(List.of(book))));
    }

    @Transactional(readOnly = true)
    public Optional<Book> findDownloadedBook(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        return bookRepo.findById(id)
                .filter(b -> Boolean.TRUE.equals(b.getDownloaded()));
    }

    /**
     * Find (or create on first use) the shop product backed by a library
     * book, so the book can flow through the normal basket/checkout
     * pipeline. Auto-created rows carry {@code bookId} (which keeps them
     * out of the public /shop listing) and remain editable under
     * Admin → Products.
     */
    @Transactional
    public Product ensurePurchaseProduct(Book book) {
        return productRepo.findByBookId(book.getId()).orElseGet(() -> createPurchaseProduct(book));
    }

    private Product createPurchaseProduct(Book book) {
        Product product = new Product();
        product.setBookId(book.getId());
        product.setName(title(book));
        product.setSlug("book-" + book.getId());
        product.setDescription(clean(book.getDescription()));
        product.setPrice(settingService.libraryEbookPrice());
        product.setImageUrl("/api/books/" + book.getId() + "/cover");
        // Copies are unlimited; checkout still caps e-books at 1 per order.
        product.setStock(1_000_000);
        product.setActive(true);
        product.setMaxPerOrder(1);
        product.setBookType("PDF");
        product.setAuthor(clean(book.getAuthor()));
        product.setPublisher(clean(book.getPublisher()));
        product.setIsbn(clean(book.getIsbn()));
        product.setLanguage(clean(book.getLanguage()));
        product.setPageCount(book.getPages());
        Integer releaseYear = year(book);
        product.setPublishYear(releaseYear == null ? null : String.valueOf(releaseYear));
        return productRepo.save(product);
    }

    /**
     * Category chips derived from the distinct downloaded values of
     * {@code books.categories}. The parent part before the first " - " is
     * used so the catalog exposes broad groups (e.g. "Fiction") instead of
     * every leaf path (e.g. "Fiction - Classics").
     */
    @Transactional(readOnly = true)
    public List<BookCategoryView> findCategories() {
        Map<String, String> nameBySlug = categoryNames();
        Map<String, Long> counts = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Book book : bookRepo.findByDownloadedTrueOrderByTitleAsc()) {
            String group = categoryGroup(book.getCategories());
            if (group != null) {
                counts.merge(group, 1L, Long::sum);
            }
        }
        List<BookCategoryView> out = new ArrayList<>(counts.size());
        counts.forEach((name, count) -> {
            String slug = slugify(name);
            out.add(new BookCategoryView(slug, name, nameBySlug.get(slug), count));
        });
        return out;
    }

    /** First non-blank cover value, so an HTTP cover wins over a local path. */
    public String preferredCover(Book book) {
        if (book == null) {
            return null;
        }
        if (notBlank(book.getCover())) {
            return book.getCover().trim();
        }
        return notBlank(book.getCoverPath()) ? book.getCoverPath().trim() : null;
    }

    /**
     * Resolves the local cover file for a book. Both {@code cover_path} and
     * {@code cover} are tried; the downloader usually prefixes paths with
     * "downloads/", which is stripped because the configured root already
     * points at that directory.
     */
    public Optional<Path> resolveCoverFile(Book book) {
        if (book == null) {
            return Optional.empty();
        }
        for (String raw : new String[]{book.getCoverPath(), book.getCover()}) {
            Path resolved = resolveLocal(raw);
            if (resolved != null) {
                return Optional.of(resolved);
            }
        }
        return Optional.empty();
    }

    private Path resolveLocal(String raw) {
        if (!notBlank(raw)) {
            return null;
        }
        String value = raw.trim().replace('\\', '/');
        if (value.startsWith("http://") || value.startsWith("https://")) {
            return null;
        }
        if (value.regionMatches(true, 0, DOWNLOADS_PREFIX, 0, DOWNLOADS_PREFIX.length())) {
            value = value.substring(DOWNLOADS_PREFIX.length());
        }
        Path candidate = Paths.get(value);
        if (!candidate.isAbsolute()) {
            candidate = libraryRoot.resolve(candidate);
        }
        candidate = candidate.normalize();
        if (!candidate.startsWith(libraryRoot) || !Files.isRegularFile(candidate) || !Files.isReadable(candidate)) {
            return null;
        }
        return candidate;
    }

    private BookView toView(Book book, Map<String, String> nameBySlug, Map<UUID, Long> priceByBook) {
        String group = categoryGroup(book.getCategories());
        String categorySlug = group == null ? null : slugify(group);
        String cover = preferredCover(book);
        String coverUrl = null;
        if (cover != null) {
            coverUrl = cover.startsWith("http://") || cover.startsWith("https://")
                    ? cover
                    : "/api/books/" + book.getId() + "/cover";
        }
        String categoryNameFa = categorySlug == null ? null : nameBySlug.get(categorySlug);

        return new BookView(
                book.getId() == null ? null : book.getId().toString(),
                title(book),
                clean(book.getAuthor()),
                clean(book.getDescription()),
                clean(book.getCategories()),
                group,
                categorySlug,
                categoryNameFa,
                coverUrl,
                clean(book.getFormat()),
                clean(book.getLanguage()),
                year(book),
                clean(book.getPublisher()),
                clean(book.getIsbn()),
                book.getPages(),
                filesize(book),
                book.getId() == null ? null : priceByBook.get(book.getId()),
                "PDF"
        );
    }

    /** Localized names (Persian) per category slug, blank rows ignored
     * (missing slugs fall back to the library's English name). */
    private Map<String, String> categoryNames() {
        Map<String, String> map = new HashMap<>();
        for (BookCategoryName row : categoryNameRepo.findAll()) {
            if (notBlank(row.getNameFa())) {
                map.put(row.getSlug(), row.getNameFa().trim());
            }
        }
        return map;
    }

    /** Admin category fixer: a manageable window of downloaded books —
     * uncategorized only, or keyword matches in title/author/categories. */
    @Transactional(readOnly = true)
    public List<BookView> findAdminBooks(String query, boolean uncategorized) {
        Pageable limit = PageRequest.of(0, 60);
        List<Book> rows;
        if (uncategorized) {
            rows = bookRepo.findDownloadedUncategorized(limit);
        } else if (notBlank(query)) {
            rows = bookRepo.searchDownloaded(query.trim(), limit);
        } else {
            rows = bookRepo.findByDownloadedTrueOrderByTitleAsc(limit);
        }
        return rows.stream().map(book -> toView(book, categoryNames(), priceMapFor(rows))).toList();
    }

    /** Admin fix: rewrite a book's raw categories value
     * ("Group - Leaf", e.g. "Fiction - Classics"; blank = uncategorized).
     * Slugs and counts re-derive from the library automatically. */
    @Transactional
    public BookView updateBookCategories(UUID id, String value) {
        Book book = bookRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Book not found: " + id));
        book.setCategories(value == null ? "" : value.trim());
        bookRepo.save(book);
        return toView(book, categoryNames(), priceMapFor(List.of(book)));
    }

    /** Upsert a localized display name for a category slug. A blank name
     * removes the translation (fallback to the library's English name). */
    @Transactional
    public void saveCategoryName(String slug, String nameFa) {
        if (!notBlank(slug)) {
            throw new RuntimeException("Category slug is required");
        }
        String cleaned = nameFa == null ? "" : nameFa.trim();
        if (cleaned.isEmpty()) {
            deleteCategoryName(slug);
            return;
        }
        BookCategoryName row = categoryNameRepo.findById(slug).orElse(new BookCategoryName(slug));
        row.setNameFa(cleaned);
        categoryNameRepo.save(row);
    }

    @Transactional
    public void deleteCategoryName(String slug) {
        if (notBlank(slug)) {
            categoryNameRepo.findById(slug).ifPresent(categoryNameRepo::delete);
        }
    }

    /**
     * Effective price per book for a whole window of catalog rows — one
     * product query plus one settings read for the entire list, so cards
     * can show prices without N+1. Books without a linked product get the
     * configured default.
     */
    private Map<UUID, Long> priceMapFor(List<Book> books) {
        long defaultPrice = settingService.libraryEbookPrice().longValue();
        Map<UUID, Long> map = new HashMap<>();
        for (Book book : books) {
            if (book.getId() != null) {
                map.put(book.getId(), defaultPrice);
            }
        }
        List<UUID> ids = books.stream().map(Book::getId).filter(Objects::nonNull).toList();
        if (!ids.isEmpty()) {
            for (Product product : productRepo.findByBookIdIn(ids)) {
                if (product.getBookId() != null) {
                    map.put(product.getBookId(), ProductService.effectivePrice(product).longValue());
                }
            }
        }
        return map;
    }

    private static String title(Book book) {
        String title = clean(book.getTitle());
        if (title != null) {
            return title;
        }
        String filename = clean(book.getFilename());
        if (filename == null) {
            return "Untitled";
        }
        int slash = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
        String name = slash >= 0 ? filename.substring(slash + 1) : filename;
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static Integer year(Book book) {
        if (book.getYear() != null) {
            return book.getYear();
        }
        String date = clean(book.getPubDate());
        if (date == null) {
            return null;
        }
        Matcher m = YEAR.matcher(date);
        return m.find() ? Integer.valueOf(m.group(1)) : null;
    }

    private static String filesize(Book book) {
        String stored = clean(book.getFilesizeString());
        if (stored != null) {
            return stored;
        }
        Long bytes = book.getSizeBytes();
        if (bytes == null || bytes <= 0) {
            return null;
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format(Locale.ROOT, "%.1f KB", kb);
        }
        double mb = kb / 1024.0;
        if (mb < 1024) {
            return String.format(Locale.ROOT, "%.1f MB", mb);
        }
        return String.format(Locale.ROOT, "%.2f GB", mb / 1024.0);
    }

    static String categoryGroup(String raw) {
        if (!notBlank(raw)) {
            return null;
        }
        String value = raw.trim();
        int separator = value.indexOf(" - ");
        if (separator > 0) {
            value = value.substring(0, separator).trim();
        }
        return value.isBlank() ? null : value;
    }

    static String slugify(String value) {
        String slug = value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        return slug.isBlank() ? "category-" + Integer.toUnsignedString(value.hashCode(), 36) : slug;
    }

    private static String clean(String value) {
        if (!notBlank(value)) {
            return null;
        }
        return value.trim();
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
