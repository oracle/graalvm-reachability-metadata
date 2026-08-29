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
import org.hibernate.context.spi.CurrentSessionContext;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SessionFactoryImplTest {

    @Test
    public void instantiatesTheConfiguredCurrentSessionContext() {
        Configuration configuration = new Configuration()
                .setProperty(AvailableSettings.URL, "jdbc:h2:mem:current-context")
                .setProperty(AvailableSettings.DRIVER, "org.h2.Driver")
                .setProperty(AvailableSettings.DIALECT, "org.hibernate.dialect.H2Dialect")
                .setProperty(
                        AvailableSettings.CURRENT_SESSION_CONTEXT_CLASS,
                        RecordingCurrentSessionContext.class.getName()
                )
                .setProperty(AvailableSettings.JAKARTA_VALIDATION_MODE, "none");

        try (SessionFactory factory = configuration.buildSessionFactory();
                Session session = factory.getCurrentSession()) {
            assertThat(session.isOpen()).isTrue();
            assertThat(session.getSessionFactory()).isSameAs(factory);
        }
    }

    public static class RecordingCurrentSessionContext implements CurrentSessionContext {
        private static final long serialVersionUID = 1L;

        private final SessionFactoryImplementor factory;

        public RecordingCurrentSessionContext(SessionFactoryImplementor factory) {
            this.factory = factory;
        }

        @Override
        public Session currentSession() {
            return factory.openSession();
        }
    }
}
