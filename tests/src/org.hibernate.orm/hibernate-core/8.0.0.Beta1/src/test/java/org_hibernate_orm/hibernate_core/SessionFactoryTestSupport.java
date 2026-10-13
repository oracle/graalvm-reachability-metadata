/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_orm.hibernate_core;

import java.util.concurrent.atomic.AtomicInteger;

import org.hibernate.boot.registry.BootstrapServiceRegistry;
import org.hibernate.cfg.Configuration;
import org.hibernate.cfg.JdbcSettings;
import org.hibernate.cfg.SchemaToolingSettings;

/// Builds isolated H2-backed Hibernate configurations for the per-class scenarios.
public final class SessionFactoryTestSupport {

    private static final AtomicInteger DATABASE_COUNTER = new AtomicInteger();

    private SessionFactoryTestSupport() {
    }

    /// Returns a configuration for the given entities backed by a fresh in-memory H2 database.
    public static Configuration h2Configuration(Class<?>... annotatedClasses) {
        return configureH2(new Configuration(), annotatedClasses);
    }

    /// Returns an H2 configuration whose bootstrap services come from the given registry.
    public static Configuration h2Configuration(BootstrapServiceRegistry bootstrapRegistry, Class<?>... annotatedClasses) {
        return configureH2(new Configuration(bootstrapRegistry), annotatedClasses);
    }

    private static Configuration configureH2(Configuration configuration, Class<?>... annotatedClasses) {
        configuration.addAnnotatedClasses(annotatedClasses);
        configuration.setProperty(JdbcSettings.JAKARTA_JDBC_DRIVER, "org.h2.Driver");
        configuration.setProperty(JdbcSettings.JAKARTA_JDBC_URL, newH2Url());
        configuration.setProperty(JdbcSettings.JAKARTA_JDBC_USER, "sa");
        configuration.setProperty(JdbcSettings.JAKARTA_JDBC_PASSWORD, "");
        configuration.setProperty(SchemaToolingSettings.HBM2DDL_AUTO, "create-drop");
        return configuration;
    }

    /// Returns a JDBC URL for a fresh, uniquely named in-memory H2 database.
    public static String newH2Url() {
        return "jdbc:h2:mem:hibernate_core_" + DATABASE_COUNTER.incrementAndGet() + ";DB_CLOSE_DELAY=-1";
    }
}
