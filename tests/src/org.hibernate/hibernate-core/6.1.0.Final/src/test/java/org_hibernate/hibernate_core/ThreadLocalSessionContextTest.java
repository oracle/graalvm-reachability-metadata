/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ThreadLocalSessionContextTest {

    @Test
    public void createsAThreadBoundSessionProxy() {
        try (SessionFactory factory = createSessionFactory()) {
            Session session = factory.getCurrentSession();
            Transaction transaction = session.beginTransaction();

            assertThat(session.isOpen()).isTrue();
            assertThat(session.getTransaction().isActive()).isTrue();

            transaction.commit();
            assertThat(session.isOpen()).isFalse();
        }
    }

    private static SessionFactory createSessionFactory() {
        return new Configuration()
                .setProperty(AvailableSettings.URL, "jdbc:h2:mem:thread-context")
                .setProperty(AvailableSettings.DRIVER, "org.h2.Driver")
                .setProperty(AvailableSettings.DIALECT, "org.hibernate.dialect.H2Dialect")
                .setProperty(AvailableSettings.CURRENT_SESSION_CONTEXT_CLASS, "thread")
                .setProperty(AvailableSettings.JAKARTA_VALIDATION_MODE, "none")
                .buildSessionFactory();
    }
}
