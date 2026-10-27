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

import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;

import static org.assertj.core.api.Assertions.assertThat;

public class EmbeddableCallbackTest {

    @Test
    public void invokesAnEmbeddableLifecycleCallback() {
        EmbeddedCallbackEntity entity = new EmbeddedCallbackEntity();
        entity.id = 1L;
        entity.details = new CallbackDetails();

        try (SessionFactory factory = createSessionFactory();
                Session session = factory.openSession()) {
            session.beginTransaction();
            session.persist(entity);
            session.getTransaction().commit();
        }

        assertThat(entity.details.created).isTrue();
    }

    private static SessionFactory createSessionFactory() {
        return new Configuration()
                .addAnnotatedClass(EmbeddedCallbackEntity.class)
                .setProperty(AvailableSettings.URL, "jdbc:h2:mem:embeddable-callback")
                .setProperty(AvailableSettings.DRIVER, "org.h2.Driver")
                .setProperty(AvailableSettings.DIALECT, "org.hibernate.dialect.H2Dialect")
                .setProperty(AvailableSettings.HBM2DDL_AUTO, "create-drop")
                .buildSessionFactory();
    }

    @Entity(name = "EmbeddedCallbackEntity")
    public static class EmbeddedCallbackEntity {
        @Id
        private Long id;
        @Embedded
        private CallbackDetails details;
    }

    @Embeddable
    public static class CallbackDetails {
        private boolean created;

        @PrePersist
        public void beforePersist() {
            created = true;
        }
    }
}
