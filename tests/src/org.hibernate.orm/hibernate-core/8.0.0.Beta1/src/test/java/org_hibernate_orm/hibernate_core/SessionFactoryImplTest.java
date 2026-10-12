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
import org.hibernate.context.spi.CurrentSessionContext;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SessionFactoryImplTest {

    @Test
    void customCurrentSessionContextClassSuppliesTheCurrentSession() {
        Configuration configuration = SessionFactoryTestSupport.h2Configuration(Memo.class);
        configuration.setProperty(
                AvailableSettings.CURRENT_SESSION_CONTEXT_CLASS,
                SharedSessionContext.class.getName());

        try (SessionFactory sessionFactory = configuration.buildSessionFactory()) {
            Session first = sessionFactory.getCurrentSession();
            Session second = sessionFactory.getCurrentSession();
            assertThat(second).isSameAs(first);

            first.beginTransaction();
            first.persist(new Memo(1L, "shared"));
            first.getTransaction().commit();
            first.close();

            String text = sessionFactory.fromTransaction(session -> session.find(Memo.class, 1L).getText());
            assertThat(text).isEqualTo("shared");
        }
    }

    public static class SharedSessionContext implements CurrentSessionContext {

        private final SessionFactoryImplementor sessionFactory;

        private Session session;

        public SharedSessionContext(SessionFactoryImplementor sessionFactory) {
            this.sessionFactory = sessionFactory;
        }

        @Override
        public synchronized Session currentSession() {
            if (session == null || !session.isOpen()) {
                session = sessionFactory.openSession();
            }
            return session;
        }
    }

    @Entity(name = "Memo")
    public static class Memo {

        @Id
        private Long id;

        private String text;

        public Memo() {
        }

        public Memo(Long id, String text) {
            this.id = id;
            this.text = text;
        }

        public String getText() {
            return text;
        }
    }
}
