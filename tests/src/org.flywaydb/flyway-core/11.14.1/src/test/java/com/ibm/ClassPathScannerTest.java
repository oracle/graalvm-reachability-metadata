/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.ibm;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import org.flywaydb.core.api.Location;
import org.flywaydb.core.api.migration.JavaMigration;
import org.flywaydb.core.api.resource.LoadableResource;
import org.flywaydb.core.internal.scanner.LocationScannerCache;
import org.flywaydb.core.internal.scanner.ResourceNameCache;
import org.flywaydb.core.internal.scanner.classpath.ClassPathScanner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassPathScannerTest extends ClassLoader {

    @TempDir
    Path classPathRoot;

    private String markerResourceName;
    private URL markerResourceUrl;

    @Test
    void scansWebSphereClasspathLocationResolvedFromClassLoaderResources() throws IOException {
        Path migrationDirectory = classPathRoot.resolve("external/migrations");
        Files.createDirectories(migrationDirectory);
        Files.writeString(migrationDirectory.resolve("flyway.location"), "");
        Files.writeString(migrationDirectory.resolve("V1__create_table.sql"), "CREATE TABLE scanned (id INT);");

        markerResourceName = "external/migrations/flyway.location";
        markerResourceUrl = migrationDirectory.resolve("flyway.location").toUri().toURL();

        ClassPathScanner<JavaMigration> scanner = new ClassPathScanner<>(
                JavaMigration.class,
                this,
                StandardCharsets.UTF_8,
                new Location("classpath:external/migrations"),
                new ResourceNameCache(),
                new LocationScannerCache(),
                false,
                false);

        assertThat(scanner.scanForResources())
                .extracting(LoadableResource::getAbsolutePath)
                .contains("external/migrations/V1__create_table.sql");
    }

    @Override
    public Enumeration<URL> getResources(String name) {
        if (name.equals(markerResourceName)) {
            return Collections.enumeration(List.of(markerResourceUrl));
        }
        return Collections.emptyEnumeration();
    }
}
