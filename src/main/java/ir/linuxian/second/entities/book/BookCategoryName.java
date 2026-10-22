package ir.linuxian.second.entities.book;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Admin-managed display names for library book categories. The public
 * catalog derives broad category groups (and their slugs) dynamically from
 * the {@code books.categories} values, so a row here only adds the
 * localized display name for a group's slug (English name comes from the
 * books library itself). Slugs without a row fall back automatically —
 * categories appearing in the library later become translatable without
 * any migration (see the category fixer under Admin → Categories).
 */
@Entity
@Table(name = "book_category_names")
public class BookCategoryName {

    /** Slug of a category group, e.g. {@code fiction}. */
    @Id
    @Getter
    @Column(nullable = false, unique = true)
    private String slug;

    /** Localized display name, e.g. Persian. Blank means "unset". */
    @Getter @Setter
    @Column(columnDefinition = "text")
    private String nameFa;

    public BookCategoryName() {
    }

    public BookCategoryName(String slug) {
        this.slug = slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }
}
