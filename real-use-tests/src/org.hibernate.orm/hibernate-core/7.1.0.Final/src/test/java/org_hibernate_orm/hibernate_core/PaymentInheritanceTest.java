/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_orm.hibernate_core;

import java.util.List;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;

import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Hibernate joined inheritance to reconcile card and bank payments through one polymorphic ledger.
@Timeout(60)
public class PaymentInheritanceTest {

    @Test
    public void listsDifferentPaymentMethodsAndPersistsCardSettlement() {
        Configuration configuration = new Configuration()
                .addAnnotatedClass(Payment.class)
                .addAnnotatedClass(CardPayment.class)
                .addAnnotatedClass(BankPayment.class)
                .setProperty("hibernate.connection.driver_class", "org.h2.Driver")
                .setProperty("hibernate.connection.url", "jdbc:h2:mem:payment_inheritance;DB_CLOSE_DELAY=-1")
                .setProperty("hibernate.hbm2ddl.auto", "create-drop")
                .setProperty("hibernate.cache.use_second_level_cache", "false")
                .setProperty("jakarta.persistence.validation.mode", "none");

        try (SessionFactory factory = configuration.buildSessionFactory()) {
            factory.inTransaction(session -> {
                session.persist(new CardPayment(1L, "ORDER-101", 4500L, "4242", "AUTH-PENDING"));
                session.persist(new BankPayment(2L, "ORDER-102", 7200L, "TRANSFER-203"));
            });

            factory.inTransaction(session -> {
                List<Payment> ledger = session.createQuery(
                        "from LedgerPayment p order by p.id", Payment.class).getResultList();
                assertEquals(List.of("ORDER-101", "ORDER-102"), ledger.stream().map(p -> p.orderNumber).toList());
                assertEquals(List.of(4500L, 7200L), ledger.stream().map(p -> p.amountCents).toList());

                CardPayment card = assertInstanceOf(CardPayment.class, ledger.get(0));
                assertEquals("4242", card.lastFour);
                assertEquals("AUTH-PENDING", card.authorizationCode);
                BankPayment bank = assertInstanceOf(BankPayment.class, ledger.get(1));
                assertEquals("TRANSFER-203", bank.transferReference);
                assertFalse(ledger.get(0).settled);
                assertFalse(ledger.get(1).settled);

                // Reconciliation updates both the shared ledger row and the card-specific row.
                ledger.get(0).settled = true;
                card.authorizationCode = "AUTH-CONFIRMED";
            });

            factory.inTransaction(session -> {
                Payment settled = session.find(Payment.class, 1L);
                CardPayment card = assertInstanceOf(CardPayment.class, settled);
                assertTrue(settled.settled);
                assertEquals("AUTH-CONFIRMED", card.authorizationCode);
                assertEquals("4242", card.lastFour);
                assertEquals(4500L, settled.amountCents);

                List<Payment> pending = session.createQuery(
                        "from LedgerPayment p where p.settled = false order by p.id", Payment.class)
                        .getResultList();
                assertEquals(1, pending.size());
                BankPayment bank = assertInstanceOf(BankPayment.class, pending.get(0));
                assertEquals("ORDER-102", pending.get(0).orderNumber);
                assertEquals("TRANSFER-203", bank.transferReference);

                List<CardPayment> cardReceipts = session.createQuery(
                        "from LedgerCardPayment c where c.settled = true", CardPayment.class).getResultList();
                assertEquals(1, cardReceipts.size());
                assertEquals("AUTH-CONFIRMED", cardReceipts.get(0).authorizationCode);
                assertEquals(11700L, session.createQuery(
                        "select sum(p.amountCents) from LedgerPayment p", Long.class).getSingleResult());
            });
        }
    }

    @Entity(name = "LedgerPayment")
    @Table(name = "ledger_payment")
    @Inheritance(strategy = InheritanceType.JOINED)
    public abstract static class Payment {
        @Id
        private Long id;

        private String orderNumber;

        private long amountCents;

        private boolean settled;

        protected Payment() {
        }

        protected Payment(Long id, String orderNumber, long amountCents) {
            this.id = id;
            this.orderNumber = orderNumber;
            this.amountCents = amountCents;
        }
    }

    @Entity(name = "LedgerCardPayment")
    @Table(name = "ledger_card_payment")
    public static final class CardPayment extends Payment {
        private String lastFour;

        private String authorizationCode;

        public CardPayment() {
        }

        public CardPayment(Long id, String orderNumber, long amountCents, String lastFour, String authorizationCode) {
            super(id, orderNumber, amountCents);
            this.lastFour = lastFour;
            this.authorizationCode = authorizationCode;
        }
    }

    @Entity(name = "LedgerBankPayment")
    @Table(name = "ledger_bank_payment")
    public static final class BankPayment extends Payment {
        private String transferReference;

        public BankPayment() {
        }

        public BankPayment(Long id, String orderNumber, long amountCents, String transferReference) {
            super(id, orderNumber, amountCents);
            this.transferReference = transferReference;
        }
    }
}
