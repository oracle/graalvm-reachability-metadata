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
import org.hibernate.annotations.SoftDelete;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/// Exercises Hibernate soft deletion to retire address-book contacts while retaining their stored details.
@Timeout(60)
public class ContactSoftDeletionTest {

    @Test
    public void hidesRetiredContactsFromLookupsAndListsWithoutErasingTheirRows() {
        Configuration configuration = new Configuration()
                .addAnnotatedClass(Contact.class)
                .setProperty("hibernate.connection.driver_class", "org.h2.Driver")
                .setProperty("hibernate.connection.url", "jdbc:h2:mem:contact_soft_deletion;DB_CLOSE_DELAY=-1")
                .setProperty("hibernate.hbm2ddl.auto", "create-drop")
                .setProperty("hibernate.cache.use_second_level_cache", "false")
                .setProperty("jakarta.persistence.validation.mode", "none");

        try (SessionFactory factory = configuration.buildSessionFactory()) {
            factory.inTransaction(session -> {
                session.persist(new Contact(1L, "Ada", "ada@example.test"));
                session.persist(new Contact(2L, "Ben", "ben@example.test"));
                session.persist(new Contact(3L, "Cora", "cora@example.test"));
            });

            factory.inTransaction(session -> {
                assertEquals(List.of("Ada", "Ben", "Cora"), session.createQuery(
                        "select c.name from AddressBookContact c order by c.name", String.class).getResultList());

                Contact retiring = session.find(Contact.class, 2L);
                assertNotNull(retiring);
                assertEquals("ben@example.test", retiring.email);
                session.remove(retiring);
            });

            factory.inTransaction(session -> {
                assertNull(session.find(Contact.class, 2L));
                assertEquals(List.of("Ada", "Cora"), session.createQuery(
                        "select c.name from AddressBookContact c order by c.name", String.class).getResultList());
                assertEquals(2L, session.createQuery(
                        "select count(c) from AddressBookContact c", Long.class).getSingleResult());

                Contact active = session.find(Contact.class, 1L);
                assertNotNull(active);
                assertEquals("ada@example.test", active.email);

                // An administrative export can still retrieve the retired contact's retained details.
                assertEquals(List.of("ben@example.test"), session.createNativeQuery(
                        "select email from address_book_contact where archived = true", String.class)
                        .getResultList());
                assertEquals(3L, session.createNativeQuery(
                        "select count(*) from address_book_contact", Long.class).getSingleResult());
            });
        }
    }

    @Entity(name = "AddressBookContact")
    @Table(name = "address_book_contact")
    @SoftDelete(columnName = "archived")
    public static final class Contact {
        @Id
        private Long id;

        @Column(nullable = false)
        private String name;

        @Column(nullable = false)
        private String email;

        public Contact() {
        }

        public Contact(Long id, String name, String email) {
            this.id = id;
            this.name = name;
            this.email = email;
        }
    }
}
