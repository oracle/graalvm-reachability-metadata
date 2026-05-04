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
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class ClassPathUtilsTest {
    private static final String RESOURCE = "io/smallrye/common/classloader/classpath-utils-resource.txt";
    private static final String CONTENT = "smallrye classpath utility resource";
    private static final String MISSING_RESOURCE = "io/smallrye/common/classloader/"
            + "missing-classpath-utils-resource.txt";

    @TempDir
    Path tempDir;

    @Test
    void consumeAsStreamsReadsResourcesFromProvidedClassLoader() throws Exception {
        Path resourcePath = createResourceFile();

        ClassLoader classLoader = new SingleResourceClassLoader(RESOURCE, resourcePath.toUri().toURL());
        List<String> consumed = new ArrayList<>();

        ClassPathUtils.consumeAsStreams(classLoader, RESOURCE, stream -> consumed.add(read(stream)));

        assertThat(consumed).containsExactly(CONTENT);
        assertThat(resourcePath).hasContent(CONTENT);
    }

    @Test
    void consumeAsPathsReadsResourcesFromProvidedClassLoader() throws Exception {
        Path resourcePath = createResourceFile();

        ClassLoader classLoader = new SingleResourceClassLoader(RESOURCE, resourcePath.toUri().toURL());
        List<Path> consumed = new ArrayList<>();

        ClassPathUtils.consumeAsPaths(classLoader, RESOURCE, consumed::add);

        assertThat(consumed).containsExactly(resourcePath);
        assertThat(consumed.get(0)).hasContent(CONTENT);
    }

    @Test
    void consumeAsStreamsQueriesSystemResourcesWhenClassLoaderIsNull() throws Exception {
        List<String> consumed = new ArrayList<>();

        ClassPathUtils.consumeAsStreams(null, MISSING_RESOURCE, stream -> consumed.add(read(stream)));

        assertThat(consumed).isEmpty();
    }

    @Test
    void consumeAsPathsQueriesSystemResourcesWhenClassLoaderIsNull() throws Exception {
        List<Path> consumed = new ArrayList<>();

        ClassPathUtils.consumeAsPaths(null, MISSING_RESOURCE, consumed::add);

        assertThat(consumed).isEmpty();
    }

    private Path createResourceFile() throws IOException {
        Path resourcePath = tempDir.resolve(RESOURCE);
        Files.createDirectories(resourcePath.getParent());
        Files.writeString(resourcePath, CONTENT, StandardCharsets.UTF_8);
        return resourcePath;
    }

    private static String read(InputStream stream) {
        try {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

final class SingleResourceClassLoader extends ClassLoader {
    private final String resourceName;
    private final URL resourceUrl;

    SingleResourceClassLoader(String resourceName, URL resourceUrl) {
        super(null);
        this.resourceName = resourceName;
        this.resourceUrl = resourceUrl;
    }

    @Override
    protected Enumeration<URL> findResources(String name) {
        if (resourceName.equals(name)) {
            return Collections.enumeration(List.of(resourceUrl));
        }
        return Collections.emptyEnumeration();
    }
}
