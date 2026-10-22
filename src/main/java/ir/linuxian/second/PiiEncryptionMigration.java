package ir.linuxian.second;

import ir.linuxian.second.repos.OrderRepo;
import ir.linuxian.second.repos.PaymentRepo;
import ir.linuxian.second.repos.TicketMessageRepo;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * One-time migration for newly encrypted PII columns (order customer
 * fields, payment cardPan, ticket message text).
 *
 * <p>Separate bean (not a method on the caller) so {@code @Transactional}
 * actually applies: self-invocation would silently bypass the proxy and
 * every write would fail with no transaction.
 */
@Component
public class PiiEncryptionMigration {

    private static final Logger log = LoggerFactory.getLogger(PiiEncryptionMigration.class);

    private final OrderRepo orderRepo;
    private final PaymentRepo paymentRepo;
    private final TicketMessageRepo ticketMessageRepo;

    @PersistenceContext
    private EntityManager entityManager;

    public PiiEncryptionMigration(OrderRepo orderRepo, PaymentRepo paymentRepo,
                                  TicketMessageRepo ticketMessageRepo) {
        this.orderRepo = orderRepo;
        this.paymentRepo = paymentRepo;
        this.ticketMessageRepo = ticketMessageRepo;
    }

    @Transactional
    public void migrate() {
        widenTextColumns();
        int orders = 0;
        for (ir.linuxian.second.entities.shopping.order.Order o : orderRepo.findAll()) {
            boolean touched = false;
            touched |= rewrite(o.getCustomerName(), o::setCustomerName, orderRepo, o);
            touched |= rewrite(o.getCustomerPhone(), o::setCustomerPhone, orderRepo, o);
            touched |= rewrite(o.getCustomerAddress(), o::setCustomerAddress, orderRepo, o);
            touched |= rewrite(o.getCustomerEmail(), o::setCustomerEmail, orderRepo, o);
            if (touched) {
                orders++;
            }
        }
        int payments = 0;
        for (ir.linuxian.second.entities.payment.Payment p : paymentRepo.findAll()) {
            if (rewrite(p.getCardPan(), p::setCardPan, paymentRepo, p)) {
                payments++;
            }
        }
        int messages = 0;
        for (ir.linuxian.second.entities.support.TicketMessage m : ticketMessageRepo.findAll()) {
            if (rewrite(m.getText(), m::setText, ticketMessageRepo, m)) {
                messages++;
            }
        }
        if (orders + payments + messages > 0) {
            log.info("Encrypted existing PII rows: {} orders, {} payments, {} ticket messages",
                    orders, payments, messages);
        }
    }

    private static boolean needsEncrypt(String v) {
        return v != null && !v.isBlank() && !v.startsWith("v1:");
    }

    /**
     * Forces Hibernate to rewrite a row through the encryption converter.
     * (Setting an equal value would not mark the entity dirty, so the value
     * is toggled through a placeholder with a flush in between.)
     */
    private static <T> boolean rewrite(String current,
                                       java.util.function.Consumer<String> setter,
                                       org.springframework.data.jpa.repository.JpaRepository<T, ?> repo,
                                       T entity) {
        if (!needsEncrypt(current)) {
            return false;
        }
        setter.accept(current + " ");
        repo.saveAndFlush(entity);
        setter.accept(current);
        repo.save(entity);
        return true;
    }

    private void widenTextColumns() {
        // {table, column} pairs that must hold ciphertext.
        String[][] cols = {
                {"orders", "customer_name"},
                {"orders", "customer_phone"},
                {"orders", "customer_address"},
                {"orders", "customer_email"},
                {"payments", "card_pan"},
                {"support_ticket_messages", "text"},
        };
        try {
            for (String[] tc : cols) {
                Object type = entityManager.createNativeQuery(
                                "SELECT data_type FROM information_schema.columns " +
                                        "WHERE table_name = ?1 AND column_name = ?2")
                        .setParameter(1, tc[0])
                        .setParameter(2, tc[1])
                        .getResultStream().findFirst().orElse(null);
                if (!"text".equalsIgnoreCase(String.valueOf(type))) {
                    entityManager.createNativeQuery(
                                    "ALTER TABLE " + tc[0] + " ALTER COLUMN " + tc[1] + " TYPE TEXT")
                            .executeUpdate();
                    log.info("Widened {}.{} to TEXT for encrypted storage", tc[0], tc[1]);
                }
            }
        } catch (Exception e) {
            log.warn("PII column widen skipped (non-PostgreSQL database?): {}", e.getMessage());
        }
    }
}
