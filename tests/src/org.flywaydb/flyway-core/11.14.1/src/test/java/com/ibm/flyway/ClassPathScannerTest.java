/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.ibm.flyway;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import org.flywaydb.core.api.Location;
import org.flywaydb.core.api.resource.LoadableResource;
import org.flywaydb.core.internal.scanner.LocationScannerCache;
import org.flywaydb.core.internal.scanner.ResourceNameCache;
import org.flywaydb.core.internal.scanner.classpath.ClassPathScanner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassPathScannerTest extends ClassLoader {

    private static final String CLASSPATH_PREFIX = "classpath:";
    private static final String LOCATION_ROOT = "db/migration";
    private static final String WEBSPHERE_LOCATION_MARKER = LOCATION_ROOT + "/flyway.location";

    @TempDir
    private Path temporaryDirectory;

    private Path migrationDirectory;

    @Test
    void resolvesWebSphereClasspathLocationMarker() throws IOException {
        migrationDirectory = Files.createDirectories(temporaryDirectory.resolve(LOCATION_ROOT));
        Files.writeString(migrationDirectory.resolve("flyway.location"), "");
        Files.writeString(migrationDirectory.resolve("V1__create_table.sql"), "CREATE TABLE test(id INT);");

        ClassPathScanner<Object> scanner = new ClassPathScanner<>(
                Object.class,
                this,
                StandardCharsets.UTF_8,
                new Location(CLASSPATH_PREFIX + LOCATION_ROOT),
                new ResourceNameCache(),
                new LocationScannerCache(),
                true,
                false);

        Collection<LoadableResource> resources = scanner.scanForResources();

        assertThat(resources)
                .extracting(LoadableResource::getAbsolutePath)
                .contains("db/migration/V1__create_table.sql");
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        if (WEBSPHERE_LOCATION_MARKER.equals(name)) {
            URL markerUrl = migrationDirectory.resolve("flyway.location").toUri().toURL();
            return Collections.enumeration(List.of(markerUrl));
        }
        return super.getResources(name);
    }
}
