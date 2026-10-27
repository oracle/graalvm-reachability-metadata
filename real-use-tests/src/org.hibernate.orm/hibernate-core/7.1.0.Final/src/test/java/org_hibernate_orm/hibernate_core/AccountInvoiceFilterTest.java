/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_orm.hibernate_core;

import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.SessionFactory;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Hibernate session filters to produce account-scoped invoice statements.
@Timeout(60)
public class AccountInvoiceFilterTest {

    @Test
    public void scopesInvoiceListsAndTotalsToTheRequestedAccount() {
        Configuration configuration = new Configuration()
                .addAnnotatedClass(Invoice.class)
                .setProperty("hibernate.connection.driver_class", "org.h2.Driver")
                .setProperty("hibernate.connection.url", "jdbc:h2:mem:account_invoice_filters;DB_CLOSE_DELAY=-1")
                .setProperty("hibernate.hbm2ddl.auto", "create-drop")
                .setProperty("hibernate.cache.use_second_level_cache", "false")
                .setProperty("jakarta.persistence.validation.mode", "none");

        try (SessionFactory factory = configuration.buildSessionFactory()) {
            factory.inTransaction(session -> {
                session.persist(new Invoice(1L, "alpine", "AL-101", 12500L));
                session.persist(new Invoice(2L, "harbor", "HB-201", 3000L));
                session.persist(new Invoice(3L, "alpine", "AL-102", 5500L));
                session.persist(new Invoice(4L, "harbor", "HB-202", 1000L));
            });

            factory.inTransaction(session -> {
                session.enableFilter("invoiceAccount").setParameter("account", "alpine");

                List<Invoice> invoices = session.createQuery(
                        "from AccountInvoice order by number", Invoice.class).getResultList();
                assertEquals(List.of("AL-101", "AL-102"), invoices.stream().map(invoice -> invoice.number).toList());
                assertEquals(List.of("alpine", "alpine"), invoices.stream().map(invoice -> invoice.account).toList());
                assertEquals(18000L, session.createQuery(
                        "select sum(i.totalCents) from AccountInvoice i", Long.class).getSingleResult());
            });

            factory.inTransaction(session -> {
                session.enableFilter("invoiceAccount").setParameter("account", "harbor");

                List<Invoice> invoices = session.createQuery(
                        "from AccountInvoice order by number", Invoice.class).getResultList();
                assertEquals(List.of("HB-201", "HB-202"), invoices.stream().map(invoice -> invoice.number).toList());
                assertEquals(List.of("harbor", "harbor"), invoices.stream().map(invoice -> invoice.account).toList());
                assertEquals(4000L, session.createQuery(
                        "select sum(i.totalCents) from AccountInvoice i", Long.class).getSingleResult());

                session.disableFilter("invoiceAccount");
                assertEquals(4L, session.createQuery(
                        "select count(i) from AccountInvoice i", Long.class).getSingleResult());
                assertEquals(22000L, session.createQuery(
                        "select sum(i.totalCents) from AccountInvoice i", Long.class).getSingleResult());
            });
        }
    }

    @Entity(name = "AccountInvoice")
    @Table(name = "account_invoice")
    @FilterDef(name = "invoiceAccount", parameters = @ParamDef(name = "account", type = String.class))
    @Filter(name = "invoiceAccount", condition = "account_key = :account")
    public static final class Invoice {
        @Id
        private Long id;

        @Column(name = "account_key", nullable = false)
        private String account;

        @Column(name = "invoice_number", nullable = false)
        private String number;

        private long totalCents;

        public Invoice() {
        }

        public Invoice(Long id, String account, String number, long totalCents) {
            this.id = id;
            this.account = account;
            this.number = number;
            this.totalCents = totalCents;
        }
    }
}
