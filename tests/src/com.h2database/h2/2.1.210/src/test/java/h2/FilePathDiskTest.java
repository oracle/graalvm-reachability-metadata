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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class FilePathDiskTest {
    @Test
    void opensClasspathResourceFromThreadContextClassLoader() throws Exception {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(new ContextResourceClassLoader());
        try (InputStream inputStream = FilePath.get("classpath:h2/context-only-resource.txt").newInputStream()) {
            assertThat(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("from context loader");
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    private static final class ContextResourceClassLoader extends ClassLoader {
        private ContextResourceClassLoader() {
            super(FilePathDiskTest.class.getClassLoader());
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            if ("h2/context-only-resource.txt".equals(name)) {
                return new ByteArrayInputStream("from context loader".getBytes(StandardCharsets.UTF_8));
            }
            return super.getResourceAsStream(name);
        }
    }
}
