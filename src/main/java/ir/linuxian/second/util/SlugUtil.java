package ir.linuxian.second.util;

public final class SlugUtil {

    private SlugUtil() {
    }

    public static String slugify(String input) {
        if (input == null) {
            return "";
        }
        String slug = input.toLowerCase().trim()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-");
        return slug.replaceAll("^-|-$", "");
    }
}
