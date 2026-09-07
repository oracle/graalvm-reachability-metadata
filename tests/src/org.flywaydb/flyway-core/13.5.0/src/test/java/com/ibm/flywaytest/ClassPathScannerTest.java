/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.ibm.flywaytest;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.api.output.MigrateResult;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassPathScannerTest {

    @Test
    void migrateScansClasspathLocationWithWebSphereStyleClassLoader(
            @TempDir final Path classpathRoot) throws IOException {
        final Path migrationDirectory = classpathRoot.resolve("db/migration");
        Files.createDirectories(migrationDirectory);
        Files.writeString(migrationDirectory.resolve("flyway.location"), "", StandardCharsets.UTF_8);
        Files.writeString(migrationDirectory.resolve("V1__create_table.sql"), """
                CREATE TABLE test
                (
                    id    INT PRIMARY KEY,
                    title VARCHAR NOT NULL
                );
                """, StandardCharsets.UTF_8);
        Files.writeString(migrationDirectory.resolve("V2__alter_table.sql"), """
                ALTER TABLE test
                    ADD COLUMN name INT NOT NULL DEFAULT 1;
                """, StandardCharsets.UTF_8);

        final WebSphereStyleClassLoader classLoader = new WebSphereStyleClassLoader(getClass().getClassLoader(),
                classpathRoot);
        final Flyway flyway = new FluentConfiguration(classLoader)
                .dataSource(getDataSource())
                .locations("classpath:db/migration")
                .loggers("slf4j")
                .load();

        final MigrateResult migration = flyway.migrate();

        assertThat(migration.success).isTrue();
        assertThat(migration.migrationsExecuted).isEqualTo(2);
        assertThat(classLoader.getRequestedResourceNames()).contains("db/migration/flyway.location");
    }

    private DataSource getDataSource() {
        final JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setUrl("jdbc:h2:mem:classpath-scanner-websphere");
        dataSource.setUser("user");
        dataSource.setPassword("password");
        return dataSource;
    }

    public static class WebSphereStyleClassLoader extends ClassLoader {
        private final Path classpathRoot;
        private final List<String> requestedResourceNames = new ArrayList<>();

        WebSphereStyleClassLoader(final ClassLoader parent, final Path classpathRoot) {
            super(parent);
            this.classpathRoot = classpathRoot;
        }

        @Override
        public Enumeration<URL> getResources(final String name) throws IOException {
            requestedResourceNames.add(name);

            final Path resource = classpathRoot.resolve(name);
            if (Files.exists(resource)) {
                return Collections.enumeration(List.of(resource.toUri().toURL()));
            }

            return super.getResources(name);
        }

        List<String> getRequestedResourceNames() {
            return requestedResourceNames;
        }
    }
}
