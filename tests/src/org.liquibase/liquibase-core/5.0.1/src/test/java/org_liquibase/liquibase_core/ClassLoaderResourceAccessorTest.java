/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_liquibase.liquibase_core;

import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.resource.Resource;
import liquibase.resource.ResourceAccessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassLoaderResourceAccessorTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void searchUsesClassLoaderResourcesAsSearchRoots() throws Exception {
        final String resourceDirectoryName = "liquibase/resource-accessor";
        final Path resourceDirectory = Files.createDirectories(temporaryDirectory.resolve(resourceDirectoryName));
        Files.writeString(resourceDirectory.resolve("example.sql"), "-- classloader resource accessor test");

        final TrackingResourceClassLoader classLoader = new TrackingResourceClassLoader(
                resourceDirectoryName,
                resourceDirectory.toUri().toURL()
        );

        try (ClassLoaderResourceAccessor resourceAccessor = new ClassLoaderResourceAccessor(classLoader)) {
            ResourceAccessor.SearchOptions searchOptions = new ResourceAccessor.SearchOptions();
            searchOptions.setTrimmedEndsWithFilter(".sql");

            final List<Resource> resources = resourceAccessor.search(resourceDirectoryName, searchOptions);

            assertThat(classLoader.requestedResources()).containsExactly(resourceDirectoryName);
            assertThat(resources)
                    .extracting(Resource::getPath)
                    .containsExactly(resourceDirectoryName + "/example.sql");
        }
    }

    private static final class TrackingResourceClassLoader extends ClassLoader {
        private final String resourceName;
        private final URL resourceUrl;
        private final List<String> requestedResources = new ArrayList<>();

        private TrackingResourceClassLoader(String resourceName, URL resourceUrl) {
            super(null);
            this.resourceName = resourceName;
            this.resourceUrl = resourceUrl;
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            requestedResources.add(name);
            if (resourceName.equals(name)) {
                return Collections.enumeration(List.of(resourceUrl));
            }
            return Collections.emptyEnumeration();
        }

        private List<String> requestedResources() {
            return requestedResources;
        }
    }
}
