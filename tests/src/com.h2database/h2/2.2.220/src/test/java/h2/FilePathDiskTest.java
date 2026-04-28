/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package h2;

import org.h2.store.fs.disk.FilePathDisk;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class FilePathDiskTest {
    private static final String CONTEXT_LOADER_RESOURCE = "h2/file-path-disk-context-loader-resource.txt";
    private static final byte[] CONTEXT_LOADER_RESOURCE_BYTES = "loaded from context class loader"
            .getBytes(StandardCharsets.UTF_8);

    @Test
    void sizeReturnsZeroForMissingClasspathResource() {
        FilePathDisk path = new FilePathDisk().getPath("classpath:h2/missing-file-path-disk-resource.txt");

        assertThat(path.size()).isZero();
    }

    @Test
    void newInputStreamReadsClasspathResourceFromContextClassLoaderFallback() throws IOException {
        Thread thread = Thread.currentThread();
        ClassLoader originalClassLoader = thread.getContextClassLoader();
        thread.setContextClassLoader(new ContextResourceClassLoader(originalClassLoader));
        try {
            FilePathDisk path = new FilePathDisk().getPath("classpath:" + CONTEXT_LOADER_RESOURCE);

            try (InputStream inputStream = path.newInputStream()) {
                assertThat(inputStream.readAllBytes()).isEqualTo(CONTEXT_LOADER_RESOURCE_BYTES);
            }
        } finally {
            thread.setContextClassLoader(originalClassLoader);
        }
    }

    private static final class ContextResourceClassLoader extends ClassLoader {
        private ContextResourceClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            if (CONTEXT_LOADER_RESOURCE.equals(name)) {
                return new ByteArrayInputStream(CONTEXT_LOADER_RESOURCE_BYTES);
            }
            return super.getResourceAsStream(name);
        }
    }
}
