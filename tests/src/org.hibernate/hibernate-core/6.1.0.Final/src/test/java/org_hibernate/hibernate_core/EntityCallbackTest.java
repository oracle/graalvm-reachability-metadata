/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;

import static org.assertj.core.api.Assertions.assertThat;

public class EntityCallbackTest {

    @Test
    public void invokesAnEntityLifecycleCallback() {
        CallbackEntity entity = new CallbackEntity();
        entity.id = 1L;

        try (SessionFactory factory = createSessionFactory();
                Session session = factory.openSession()) {
            session.beginTransaction();
            session.persist(entity);
            session.getTransaction().commit();
        }

        assertThat(entity.created).isTrue();
    }

    private static SessionFactory createSessionFactory() {
        return new Configuration()
                .addAnnotatedClass(CallbackEntity.class)
                .setProperty(AvailableSettings.URL, "jdbc:h2:mem:entity-callback")
                .setProperty(AvailableSettings.DRIVER, "org.h2.Driver")
                .setProperty(AvailableSettings.DIALECT, "org.hibernate.dialect.H2Dialect")
                .setProperty(AvailableSettings.HBM2DDL_AUTO, "create-drop")
                .buildSessionFactory();
    }

    @Entity(name = "CallbackEntity")
    public static class CallbackEntity {
        @Id
        private Long id;
        private boolean created;

        @PrePersist
        public void beforePersist() {
            created = true;
        }
    }
}
