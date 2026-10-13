/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_orm.hibernate_core;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Configuration;
import org.hibernate.dialect.H2Dialect;
import org.hibernate.engine.jdbc.dialect.spi.BasicDialectResolver;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DialectResolverInitiatorTest {

    @Test
    void configuredDialectResolverSelectsTheDialect() {
        Configuration configuration = SessionFactoryTestSupport.h2Configuration(Planet.class);
        configuration.setProperty(AvailableSettings.DIALECT_RESOLVERS, LegacyH2DialectResolver.class.getName());

        try (SessionFactory sessionFactory = configuration.buildSessionFactory()) {
            sessionFactory.inTransaction(session -> {
                session.persist(new Planet(1L, "first"));
                session.persist(new Planet(2L, "second"));
            });

            assertThat(sessionFactory.unwrap(SessionFactoryImplementor.class).getJdbcServices().getDialect())
                    .isExactlyInstanceOf(LegacyH2Dialect.class);
            String name = sessionFactory.fromTransaction(session -> session.find(Planet.class, 1L).getLabel());
            assertThat(name).isEqualTo("first");
        }
    }

    public static class LegacyH2Dialect extends H2Dialect {
    }

    public static class LegacyH2DialectResolver extends BasicDialectResolver {

        public LegacyH2DialectResolver() {
            super("H2", LegacyH2Dialect.class);
        }
    }

    @Entity(name = "Planet")
    public static class Planet {

        @Id
        private Long id;

        private String label;

        public Planet() {
        }

        public Planet(Long id, String label) {
            this.id = id;
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }
}
