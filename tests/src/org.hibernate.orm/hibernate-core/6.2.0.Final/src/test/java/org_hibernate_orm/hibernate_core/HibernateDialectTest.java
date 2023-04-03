/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_orm.hibernate_core;

import org.hibernate.boot.registry.internal.BootstrapServiceRegistryImpl;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.dialect.Dialect;
import org.hibernate.engine.jdbc.dialect.internal.DialectFactoryImpl;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class HibernateDialectTest {

    @ParameterizedTest
    @MethodSource("provideHibernateDialects")
    void testHibernateDialects(String hibernateDialect) {
        Map<String, Object> configValues = new HashMap<>();
        configValues.put(AvailableSettings.DIALECT, hibernateDialect);
        DialectFactoryImpl dialectFactoryImpl = new DialectFactoryImpl();
        dialectFactoryImpl.injectServices(new BootstrapServiceRegistryImpl());
        Dialect dialect = dialectFactoryImpl.buildDialect(configValues, null);
        assertThat(dialect).isNotNull();
    }

    private static Stream<Arguments> provideHibernateDialects() {
        String dialectPackage = "org.hibernate.dialect.";

        List<String> hibernateDialects = Arrays.asList(
                "MariaDB103Dialect",
                "MariaDB106Dialect",
                "MySQL8Dialect",
                "MySQL57Dialect",
                "Oracle12cDialect",
                "PostgresPlusDialect",
                "PostgreSQL10Dialect",
                "SQLServer2008Dialect",
                "SQLServer2012Dialect",
                "SQLServer2016Dialect"
        );

        return hibernateDialects.stream().map(dialect -> Arguments.of(dialectPackage + dialect));
    }
}
