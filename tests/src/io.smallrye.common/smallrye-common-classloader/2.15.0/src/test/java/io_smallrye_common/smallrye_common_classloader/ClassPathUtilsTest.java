/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_smallrye_common.smallrye_common_classloader;

import static org.assertj.core.api.Assertions.assertThat;

import io.smallrye.common.classloader.ClassPathUtils;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class ClassPathUtilsTest {
    private static final String RESOURCE_NAME = "class-path-utils-resource.txt";
    private static final String RESOURCE_CONTENT = "smallrye-classloader-resource";
    private static final String MISSING_SYSTEM_RESOURCE = "missing/io-smallrye-common-classpath-utils-resource.txt";

    @TempDir
    Path tempDir;

    @Test
    void consumeAsStreamsReadsResourcesFromProvidedClassLoader() throws IOException {
        Path resource = writeResourceFile();
        ClassLoader classLoader = new SingleResourceClassLoader(RESOURCE_NAME, resource.toUri().toURL());
        StringBuilder consumed = new StringBuilder();

        ClassPathUtils.consumeAsStreams(classLoader, RESOURCE_NAME, stream -> consumed.append(readString(stream)));

        assertThat(consumed).hasToString(RESOURCE_CONTENT);
    }

    @Test
    void consumeAsPathsResolvesResourcesFromProvidedClassLoader() throws IOException {
        Path resource = writeResourceFile();
        ClassLoader classLoader = new SingleResourceClassLoader(RESOURCE_NAME, resource.toUri().toURL());
        List<Path> consumed = new ArrayList<>();

        ClassPathUtils.consumeAsPaths(classLoader, RESOURCE_NAME, consumed::add);

        assertThat(consumed).containsExactly(resource);
    }

    @Test
    void consumeAsStreamsUsesSystemResourcesForNullClassLoader() throws IOException {
        AtomicInteger consumed = new AtomicInteger();

        ClassPathUtils.consumeAsStreams(null, MISSING_SYSTEM_RESOURCE, stream -> consumed.incrementAndGet());

        assertThat(consumed).hasValue(0);
    }

    @Test
    void consumeAsPathsUsesSystemResourcesForNullClassLoader() throws IOException {
        AtomicInteger consumed = new AtomicInteger();

        ClassPathUtils.consumeAsPaths(null, MISSING_SYSTEM_RESOURCE, path -> consumed.incrementAndGet());

        assertThat(consumed).hasValue(0);
    }

    private Path writeResourceFile() throws IOException {
        Path resource = tempDir.resolve(RESOURCE_NAME);
        Files.writeString(resource, RESOURCE_CONTENT, StandardCharsets.UTF_8);
        return resource;
    }

    private static String readString(InputStream stream) {
        try {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read resource stream", e);
        }
    }

    private static final class SingleResourceClassLoader extends ClassLoader {
        private final String resourceName;
        private final URL resourceUrl;

        private SingleResourceClassLoader(String resourceName, URL resourceUrl) {
            super(null);
            this.resourceName = resourceName;
            this.resourceUrl = resourceUrl;
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            if (resourceName.equals(name)) {
                return Collections.enumeration(List.of(resourceUrl));
            }
            return Collections.emptyEnumeration();
        }
    }
}
