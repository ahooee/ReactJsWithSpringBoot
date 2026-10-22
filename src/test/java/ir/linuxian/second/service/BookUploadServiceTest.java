package ir.linuxian.second.service;

import ir.linuxian.second.entities.book.Book;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BookUploadServiceTest {

    private static Book book(String filename, String relPath) {
        Book b = new Book();
        b.setFilename(filename);
        b.setRelPath(relPath);
        return b;
    }

    @Test
    void findsFileByRelPath(@TempDir Path root) throws Exception {
        Path file = Files.writeString(root.resolve("Dune.epub"), "x");
        assertEquals(file, BookUploadService.resolveBookFile(root, book("downloads/Dune.epub", "Dune.epub")));
    }

    @Test
    void findsFileByDownloadsPrefixedNameWhenRootIsParent(@TempDir Path root) throws Exception {
        Path downloads = Files.createDirectories(root.resolve("downloads"));
        Path file = Files.writeString(downloads.resolve("Dune.epub"), "x");
        assertEquals(file, BookUploadService.resolveBookFile(root, book("downloads/Dune.epub", "Dune.epub")));
    }

    @Test
    void rejectsPathOutsideRoot(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("secret.epub"), "x");
        assertNull(BookUploadService.resolveBookFile(root, book("../secret.epub", "../secret.epub")));
    }
}
