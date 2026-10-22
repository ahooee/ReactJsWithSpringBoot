package ir.linuxian.second.dto;

/**
 * A language filter derived from the distinct {@code books.language} values
 * (case-insensitive; blank, "unknown" and ISO {@code zxx = no linguistic
 * content} are grouped as "Unknown").
 */
public record BookLanguageView(
        String language,
        long count
) {
}
