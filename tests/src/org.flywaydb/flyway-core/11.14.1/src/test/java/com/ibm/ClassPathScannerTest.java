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
import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.resource.LoadableResource;
import org.flywaydb.core.internal.scanner.LocationScannerCache;
import org.flywaydb.core.internal.scanner.ResourceNameCache;
import org.flywaydb.core.internal.scanner.classpath.ClassPathScanner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassPathScannerTest {
    private static final String LOCATION = "db/migration";
    private static final String MARKER = LOCATION + "/flyway.location";
    private static final String MIGRATION = LOCATION + "/V1__scanner_test.sql";

    @TempDir
    Path classPathRoot;

    @Test
    void scansWebSphereStyleLocationMarkerResources() throws IOException {
        Path migrationDirectory = classPathRoot.resolve("db").resolve("migration");
        Files.createDirectories(migrationDirectory);
        Files.writeString(migrationDirectory.resolve("flyway.location"), "", StandardCharsets.UTF_8);
        Files.writeString(migrationDirectory.resolve("V1__scanner_test.sql"), "select 1;", StandardCharsets.UTF_8);

        ClassLoader classLoader = new FixedLocationClassLoader(classPathRoot, getClass().getClassLoader());
        ClassPathScanner<Callback> scanner = new ClassPathScanner<>(
                Callback.class,
                classLoader,
                StandardCharsets.UTF_8,
                new Location("classpath:" + LOCATION),
                new ResourceNameCache(),
                new LocationScannerCache(),
                true,
                false);

        List<String> resourcePaths = scanner.scanForResources().stream()
                .map(LoadableResource::getAbsolutePath)
                .toList();

        assertThat(resourcePaths).contains(MIGRATION);
    }

    private static final class FixedLocationClassLoader extends ClassLoader {
        private final Path classPathRoot;

        private FixedLocationClassLoader(Path classPathRoot, ClassLoader parent) {
            super(parent);
            this.classPathRoot = classPathRoot;
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            if (!MARKER.equals(name) && !MIGRATION.equals(name)) {
                return super.getResources(name);
            }

            Path resource = resolveResource(name);
            if (!Files.exists(resource)) {
                return Collections.emptyEnumeration();
            }
            return Collections.enumeration(List.of(resource.toUri().toURL()));
        }

        private Path resolveResource(String name) {
            Path resource = classPathRoot;
            for (String part : name.split("/")) {
                resource = resource.resolve(part);
            }
            return resource;
        }
    }
}
