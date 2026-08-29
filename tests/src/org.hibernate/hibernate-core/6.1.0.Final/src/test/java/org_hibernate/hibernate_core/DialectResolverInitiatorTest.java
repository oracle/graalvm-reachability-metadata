/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.dialect.Dialect;
import org.hibernate.dialect.H2Dialect;
import org.hibernate.engine.jdbc.dialect.spi.DialectResolutionInfo;
import org.hibernate.engine.jdbc.dialect.spi.DialectResolver;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DialectResolverInitiatorTest {

    @Test
    public void usesTheConfiguredDialectResolver() {
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting(
                        AvailableSettings.DIALECT_RESOLVERS,
                        RecordingDialectResolver.class.getName()
                )
                .build();
        try {
            Dialect dialect = registry.getService(DialectResolver.class).resolveDialect(null);

            assertThat(dialect).isInstanceOf(H2Dialect.class);
        }
        finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    public static class RecordingDialectResolver implements DialectResolver {
        @Override
        public Dialect resolveDialect(DialectResolutionInfo info) {
            return new H2Dialect();
        }
    }
}
