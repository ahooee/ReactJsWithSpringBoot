package ir.linuxian.second.dto;

/**
 * Public representation of a book from the {@code books} table. Covers are
 * served through {@code /api/books/{id}/cover} when the stored value is a
 * local library path.
 */
public record BookView(
        String id,
        String title,
        String author,
        String description,
        /** Full value of books.categories (e.g. "Fiction - Classics"). */
        String categories,
        /** Broad category derived from books.categories (e.g. "Fiction"). */
        String category,
        String categorySlug,
        String coverUrl,
        String format,
        String language,
        Integer year,
        String publisher,
        String isbn,
        Integer pages,
        String filesize,
        /**
         * Effective shop price in rials (linked product, or the configured
         * default for catalog books). Falls back to the configured default
         * before the product exists. Only guaranteed on the detail endpoint.
         */
        Long price,
        /** Kept for the storefront card/filter code; all table books are e-books. */
        String bookType
) {
}
