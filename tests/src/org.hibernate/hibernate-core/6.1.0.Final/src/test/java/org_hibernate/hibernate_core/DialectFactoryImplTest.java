/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.SessionFactory;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Configuration;
import org.hibernate.dialect.H2Dialect;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DialectFactoryImplTest {

    @Test
    public void createsADialectUsingJdbcResolutionInformation() {
        Configuration configuration = new Configuration()
                .setProperty(AvailableSettings.URL, "jdbc:h2:mem:dialect-factory")
                .setProperty(AvailableSettings.DRIVER, "org.h2.Driver")
                .setProperty(AvailableSettings.DIALECT, H2Dialect.class.getName())
                .setProperty(AvailableSettings.JAKARTA_VALIDATION_MODE, "none");

        try (SessionFactory factory = configuration.buildSessionFactory()) {
            assertThat(((SessionFactoryImplementor) factory).getJdbcServices().getDialect())
                    .isInstanceOf(H2Dialect.class);
        }
    }
}
