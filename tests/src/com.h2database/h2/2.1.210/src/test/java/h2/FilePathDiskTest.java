/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package h2;

import org.h2.store.fs.FilePath;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class FilePathDiskTest {
    private static final String CLASSPATH_RESOURCE = "h2-file-path-disk/class-resource.txt";
    private static final String CLASSPATH_RESOURCE_CONTENT = "file-path-disk-resource";
    private static final String CONTEXT_ONLY_RESOURCE = "h2-file-path-disk/context-only-resource.txt";
    private static final String CONTEXT_ONLY_RESOURCE_CONTENT = "context-loader-resource";

    @Test
    void resolvesClasspathResourceSizeThroughClassResourceLookup() {
        FilePath path = FilePath.get("classpath:" + CLASSPATH_RESOURCE);

        long size = path.size();
        long fileResourceSize = (CLASSPATH_RESOURCE_CONTENT + "\n").getBytes(StandardCharsets.UTF_8).length;

        assertThat(size).isIn(0L, fileResourceSize);
    }

    @Test
    void opensClasspathResourceWithClassResourceStreamLookup() throws IOException {
        FilePath path = FilePath.get("classpath:" + CLASSPATH_RESOURCE);

        try (InputStream input = path.newInputStream()) {
            String content = new String(input.readAllBytes(), StandardCharsets.UTF_8).trim();
            assertThat(content).isEqualTo(CLASSPATH_RESOURCE_CONTENT);
        }
    }

    @Test
    void fallsBackToContextClassLoaderForClasspathResourceStream() throws IOException {
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        ClassLoader resourceClassLoader = new ClassLoader(originalContextClassLoader) {
            @Override
            public InputStream getResourceAsStream(String name) {
                if (CONTEXT_ONLY_RESOURCE.equals(name)) {
                    byte[] content = CONTEXT_ONLY_RESOURCE_CONTENT.getBytes(StandardCharsets.UTF_8);
                    return new ByteArrayInputStream(content);
                }
                return super.getResourceAsStream(name);
            }
        };
        Thread.currentThread().setContextClassLoader(resourceClassLoader);
        try {
            FilePath path = FilePath.get("classpath:" + CONTEXT_ONLY_RESOURCE);

            try (InputStream input = path.newInputStream()) {
                String content = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                assertThat(content).isEqualTo(CONTEXT_ONLY_RESOURCE_CONTENT);
            }
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }
}
