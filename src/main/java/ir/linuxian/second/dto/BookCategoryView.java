package ir.linuxian.second.dto;

/** A category chip derived from the distinct {@code books.categories} values. */
public record BookCategoryView(
        String slug,
        String name,
        long count
) {
}
