/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_liquibase.liquibase_core;

import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.resource.Resource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassLoaderResourceAccessorTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void searchUsesClassLoaderResourcesAsSearchRoots() throws Exception {
        String resourceDirectoryName = "liquibase/resource-accessor";
        Path resourceDirectory = Files.createDirectories(temporaryDirectory.resolve(resourceDirectoryName));
        Files.writeString(resourceDirectory.resolve("example.sql"), "-- resource accessor test");
        TrackingResourceClassLoader classLoader = new TrackingResourceClassLoader(
                resourceDirectoryName,
                resourceDirectory.toUri().toURL()
        );

        try (ClassLoaderResourceAccessor resourceAccessor = new ClassLoaderResourceAccessor(classLoader)) {
            List<Resource> resources = resourceAccessor.search(resourceDirectoryName, false);

            assertThat(classLoader.getRequestedResource()).isEqualTo(resourceDirectoryName);
            assertThat(resources)
                    .extracting(Resource::getPath)
                    .containsExactly(resourceDirectoryName + "/example.sql");
        }
    }

    private static final class TrackingResourceClassLoader extends ClassLoader {
        private final String resourceName;
        private final URL resourceUrl;
        private String requestedResource;

        private TrackingResourceClassLoader(String resourceName, URL resourceUrl) {
            super(null);
            this.resourceName = resourceName;
            this.resourceUrl = resourceUrl;
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            requestedResource = name;
            if (resourceName.equals(name)) {
                return Collections.enumeration(List.of(resourceUrl));
            }
            return Collections.emptyEnumeration();
        }

        private String getRequestedResource() {
            return requestedResource;
        }
    }
}
