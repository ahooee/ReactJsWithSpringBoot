package ir.linuxian.second.entities.book;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;

/**
 * Links a book to the Telegram message that carries its file. The book_id
 * column is a foreign key to {@code books.id}, so the two tables can be
 * joined directly.
 */
@Entity
@Table(name = "books_messages")
public class BookMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Getter
    private Long id;

    @Getter
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @Getter
    @Column(name = "chat_id", nullable = false)
    private Long chatId;

    @Getter
    @Column(name = "message_id", nullable = false)
    private Long messageId;

    @Getter
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public BookMessage() {
    }

    public BookMessage(Book book, long chatId, long messageId) {
        this.book = book;
        this.chatId = chatId;
        this.messageId = messageId;
    }
}
