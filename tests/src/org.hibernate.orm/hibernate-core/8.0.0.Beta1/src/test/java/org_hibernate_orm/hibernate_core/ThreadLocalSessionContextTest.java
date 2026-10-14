/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_orm.hibernate_core;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ThreadLocalSessionContextTest {

    @Test
    void threadBoundSessionIsReusedUntilTheTransactionCompletes() {
        Configuration configuration = SessionFactoryTestSupport.h2Configuration(Reminder.class);
        configuration.setProperty(AvailableSettings.CURRENT_SESSION_CONTEXT_CLASS, "thread");

        try (SessionFactory sessionFactory = configuration.buildSessionFactory()) {
            Session session = sessionFactory.getCurrentSession();
            session.beginTransaction();
            session.persist(new Reminder(1L, "water the plants"));
            assertThat(sessionFactory.getCurrentSession()).isSameAs(session);
            assertThat(session.contains(session.find(Reminder.class, 1L))).isTrue();
            session.getTransaction().commit();

            Session nextSession = sessionFactory.getCurrentSession();
            assertThat(nextSession).isNotSameAs(session);
            nextSession.beginTransaction();
            String text = nextSession.find(Reminder.class, 1L).getText();
            nextSession.getTransaction().commit();

            assertThat(text).isEqualTo("water the plants");
        }
    }

    @Entity(name = "Reminder")
    public static class Reminder {

        @Id
        private Long id;

        private String text;

        public Reminder() {
        }

        public Reminder(Long id, String text) {
            this.id = id;
            this.text = text;
        }

        public String getText() {
            return text;
        }
    }
}
