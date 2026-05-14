/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.ibm.flywaytest;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.api.output.MigrateResult;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassPathScannerTest extends ClassLoader {

    @TempDir
    private Path classpathRoot;

    public ClassPathScannerTest() {
        super(Thread.currentThread().getContextClassLoader());
    }

    @Test
    void migratesSqlResourcesFoundThroughWebSphereClassLoaderLocationMarker() throws IOException {
        final Path migrationDirectory = Files.createDirectories(classpathRoot.resolve("db/migration"));
        Files.createFile(migrationDirectory.resolve("flyway.location"));
        Files.writeString(migrationDirectory.resolve("V1__create_classpath_scanner_table.sql"), """
                CREATE TABLE classpath_scanner_resource (
                    id INT PRIMARY KEY,
                    description VARCHAR(32)
                );
                """);

        final Flyway flyway = new FluentConfiguration(this)
                .dataSource(getDataSource())
                .locations("classpath:db/migration")
                .load();

        final MigrateResult result = flyway.migrate();

        assertThat(result.success).isTrue();
        assertThat(result.migrationsExecuted).isEqualTo(1);
    }

    @Override
    public URL getResource(final String name) {
        try {
            final URL url = getLocalResource(name);
            if (url != null) {
                return url;
            }
        } catch (final IOException e) {
            return null;
        }
        return super.getResource(name);
    }

    @Override
    public Enumeration<URL> getResources(final String name) throws IOException {
        final URL url = getLocalResource(name);
        if (url != null) {
            return Collections.enumeration(List.of(url));
        }
        return super.getResources(name);
    }

    private URL getLocalResource(final String name) throws IOException {
        final Path resource = classpathRoot.resolve(name).normalize();
        if (!resource.startsWith(classpathRoot) || !Files.exists(resource)) {
            return null;
        }
        return resource.toUri().toURL();
    }

    private static DataSource getDataSource() {
        final JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setUrl("jdbc:h2:mem:classpath_scanner_" + UUID.randomUUID().toString().replace("-", ""));
        dataSource.setUser("user");
        dataSource.setPassword("password");
        return dataSource;
    }
}
