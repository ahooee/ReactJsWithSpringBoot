package ir.linuxian.second.dto;

/** A category chip derived from the distinct {@code books.categories} values. */
public record BookCategoryView(
        String slug,
        String name,
        /** Admin-managed translation (e.g. Persian); null → UI falls back to {@code name}. */
        String nameFa,
        long count
) {
}
