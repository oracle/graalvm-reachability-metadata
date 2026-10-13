/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_orm.hibernate_core;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PreRemove;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ListenerCallbackTest {

    private static final List<String> AUDIT = new CopyOnWriteArrayList<>();

    @Test
    void entityListenerCallbacksAreInvokedWithTheEntity() {
        AUDIT.clear();
        try (SessionFactory sessionFactory = SessionFactoryTestSupport
                .h2Configuration(Account.class)
                .buildSessionFactory()) {
            sessionFactory.inTransaction(session -> session.persist(new Account(1L, "ACME")));
            sessionFactory.inTransaction(session -> session.remove(session.find(Account.class, 1L)));
        }

        assertThat(AUDIT).containsExactly("created ACME", "removing ACME");
    }

    public static class AuditListener {

        @PostPersist
        public void created(Account account) {
            AUDIT.add("created " + account.getOwner());
        }

        @PreRemove
        public void removing(Account account) {
            AUDIT.add("removing " + account.getOwner());
        }
    }

    @Entity(name = "Account")
    @EntityListeners(AuditListener.class)
    public static class Account {

        @Id
        private Long id;

        private String owner;

        public Account() {
        }

        public Account(Long id, String owner) {
            this.id = id;
            this.owner = owner;
        }

        public String getOwner() {
            return owner;
        }
    }
}
