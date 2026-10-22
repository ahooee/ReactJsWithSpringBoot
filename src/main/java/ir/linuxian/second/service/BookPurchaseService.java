package ir.linuxian.second.service;

import ir.linuxian.second.entities.book.Book;
import ir.linuxian.second.entities.product.Product;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Glue between the read-only books catalog and the shopping basket: turns a
 * catalog book into its shop product on first purchase
 * (see {@link BookCatalogService#ensurePurchaseProduct}) and then adds that
 * product to the caller's basket with the ordinary basket rules
 * (e-books are capped at 1 per order, price snapshot happens at checkout).
 */
@Service
public class BookPurchaseService {

    private final BookCatalogService catalog;
    private final BasketService basketService;

    public BookPurchaseService(BookCatalogService catalog, BasketService basketService) {
        this.catalog = catalog;
        this.basketService = basketService;
    }

    @Transactional
    public Map<String, Object> purchase(String token, String username, UUID bookId, int qty) {
        Book book = catalog.findDownloadedBook(bookId)
                .orElseThrow(() -> new RuntimeException("Book not found"));
        Product product = catalog.ensurePurchaseProduct(book);
        return basketService.addItem(token, username, product.getId(), qty > 0 ? qty : 1);
    }
}
