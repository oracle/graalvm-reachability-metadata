/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus.plexus_resources;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.codehaus.plexus.resource.PlexusResource;
import org.codehaus.plexus.resource.loader.ThreadContextClasspathResourceLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class ThreadContextClasspathResourceLoaderTest {
    @TempDir
    private Path temporaryDirectory;

    @Test
    void resolvesResourceFromThreadContextClassLoader() throws Exception {
        Path resourceFile = temporaryDirectory.resolve("context-resource.txt");
        Files.writeString(resourceFile, "loaded through the thread context class loader", StandardCharsets.UTF_8);

        String resourceName = "fixtures/context-resource.txt";
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        ClassLoader contextClassLoader = new SingleResourceClassLoader(resourceName, resourceFile.toUri().toURL());
        Thread.currentThread().setContextClassLoader(contextClassLoader);
        try {
            ThreadContextClasspathResourceLoader loader = new ThreadContextClasspathResourceLoader();

            PlexusResource resource = loader.getResource("/" + resourceName);

            assertThat(resource.getURL()).isEqualTo(resourceFile.toUri().toURL());
            assertThat(resource.getName()).isEqualTo(resourceFile.toUri().toURL().toExternalForm());
            try (InputStream inputStream = resource.getInputStream()) {
                assertThat(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8))
                        .isEqualTo("loaded through the thread context class loader");
            }
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    private static final class SingleResourceClassLoader extends ClassLoader {
        private final String resourceName;
        private final URL resourceUrl;

        SingleResourceClassLoader(String resourceName, URL resourceUrl) {
            super(null);
            this.resourceName = resourceName;
            this.resourceUrl = resourceUrl;
        }

        @Override
        protected URL findResource(String name) {
            if (resourceName.equals(name)) {
                return resourceUrl;
            }
            return null;
        }
    }
}
