package ir.linuxian.second.dto;

import java.util.List;

/**
 * Horizontal reel strips for the books catalog page (Digikala-style rows):
 * admin-curated featured books, best-rated recommendations and the
 * best-selling titles from real orders.
 */
public record BookReelsView(
        List<BookView> featured,
        List<BookView> recommended,
        List<BookView> topSellers) {
}
