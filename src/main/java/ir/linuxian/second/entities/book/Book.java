package ir.linuxian.second.entities.book;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.DynamicUpdate;

import java.time.Instant;
import java.util.UUID;

/**
 * A row from the existing {@code books} table (shared with the zlibrary /
 * libgen downloaders). The table is owned by the downloader, not by this
 * application: the mapping follows its real schema (UUID primary key,
 * {@code categories}, {@code cover}/{@code cover_path}, ...).
 */
@Entity
@DynamicUpdate
@Table(name = "books")
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Getter
    private UUID id;

    @Getter @Setter
    @Column(name = "library_id", nullable = false)
    private Integer libraryId;

    /** Path relative to the library root. */
    @Getter @Setter
    @Column(name = "rel_path", nullable = false, columnDefinition = "text")
    private String relPath;

    /** Path stored by the downloader, usually prefixed with "downloads/". */
    @Getter @Setter
    @Column(nullable = false, columnDefinition = "text")
    private String filename;

    @Getter @Setter
    @Column(columnDefinition = "text")
    private String format;

    @Getter @Setter
    @Column(columnDefinition = "text")
    private String title;

    @Getter @Setter
    @Column(columnDefinition = "text")
    private String author;

    @Getter @Setter
    @Column(columnDefinition = "text")
    private String isbn;

    @Getter @Setter
    private Integer pages;

    @Getter @Setter
    @Column(columnDefinition = "text")
    private String publisher;

    @Getter @Setter
    @Column(columnDefinition = "text")
    private String language;

    @Getter @Setter
    @Column(name = "pub_date", columnDefinition = "text")
    private String pubDate;

    @Getter @Setter
    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Getter @Setter
    private Double mtime;

    @Getter @Setter
    @Column(columnDefinition = "text")
    private String error;

    @Getter @Setter
    @Column(name = "meta_source", columnDefinition = "text")
    private String metaSource;

    @Getter @Setter
    @Column(name = "indexed_at")
    private Instant indexedAt;

    @Getter @Setter
    @Column(columnDefinition = "text")
    private String md5;

    @Getter @Setter
    @Column(columnDefinition = "text")
    private String mirror;

    @Getter @Setter
    @Column(nullable = false)
    @ColumnDefault("false")
    private Boolean downloaded = false;

    @Getter @Setter
    @Column(name = "libgen_id", columnDefinition = "text")
    private String libgenId;

    @Getter @Setter
    @Column(name = "zlib_id", columnDefinition = "text")
    private String zlibId;

    @Getter @Setter
    @Column(name = "zlib_hash", columnDefinition = "text")
    private String zlibHash;

    @Getter @Setter
    @Column(name = "book_url", columnDefinition = "text")
    private String bookUrl;

    @Getter @Setter
    private Integer year;

    @Getter @Setter
    @Column(columnDefinition = "text")
    private String series;

    @Getter @Setter
    @Column(columnDefinition = "text")
    private String volume;

    @Getter @Setter
    @Column(columnDefinition = "text")
    private String edition;

    /** Cover image URL or library-relative path. */
    @Getter @Setter
    @Column(columnDefinition = "text")
    private String cover;

    /** Local cover path relative to the library root. */
    @Getter @Setter
    @Column(name = "cover_path", columnDefinition = "text")
    private String coverPath;

    @Getter @Setter
    @Column(columnDefinition = "text")
    private String description;

    @Getter @Setter
    @Column(name = "filesize_string", columnDefinition = "text")
    private String filesizeString;

    @Getter @Setter
    @Column(columnDefinition = "text")
    private String sha256;

    @Getter @Setter
    @Column(name = "interest_score", columnDefinition = "text")
    private String interestScore;

    @Getter @Setter
    @Column(name = "quality_score", columnDefinition = "text")
    private String qualityScore;

    @Getter @Setter
    @Column(name = "content_type", columnDefinition = "text")
    private String contentType;

    @Getter @Setter
    @Column(columnDefinition = "text")
    private String dl;

    /** True once this book file has been sent to Telegram. */
    @Getter @Setter
    @Column(nullable = false)
    @ColumnDefault("false")
    private Boolean uploaded = false;

    /** Z-Library category path, e.g. "Fiction - Classics". */
    @Getter @Setter
    @Column(columnDefinition = "text")
    private String categories;

    public Book() {
    }
}
