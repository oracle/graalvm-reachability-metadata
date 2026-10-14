/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_orm.hibernate_core;

import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SetterMethodImplTest {

    @Test
    void propertyAccessHydratesStateThroughSetters() {
        try (SessionFactory sessionFactory = SessionFactoryTestSupport
                .h2Configuration(Username.class)
                .buildSessionFactory()) {
            sessionFactory.inTransaction(session -> {
                Username username = new Username();
                username.setId(1L);
                username.setHandle("  Grace.Hopper ");
                session.persist(username);
            });
            String loaded = sessionFactory.fromTransaction(session -> session.find(Username.class, 1L).getHandle());

            assertThat(loaded).isEqualTo("grace.hopper");
        }
    }

    @Entity(name = "Username")
    @Access(AccessType.PROPERTY)
    public static class Username {

        private Long id;

        private String handle;

        @Id
        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getHandle() {
            return handle;
        }

        public void setHandle(String handle) {
            this.handle = handle.trim().toLowerCase();
        }
    }
}
