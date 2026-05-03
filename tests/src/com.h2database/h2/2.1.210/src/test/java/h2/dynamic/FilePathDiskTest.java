/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package h2.dynamic;

import org.h2.store.fs.FileUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class FilePathDiskTest {
    @TempDir
    Path tempDir;

    @Test
    void loadsClasspathResourceFromThreadContextClassLoader() throws Exception {
        Path resource = tempDir.resolve("context-resource.txt");
        Files.writeString(resource, "loaded from context", StandardCharsets.UTF_8);
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        URL[] urls = new URL[] { tempDir.toUri().toURL() };
        try (URLClassLoader classLoader = new URLClassLoader(urls, null)) {
            Thread.currentThread().setContextClassLoader(classLoader);

            try (InputStream stream = FileUtils.newInputStream("classpath:context-resource.txt")) {
                assertThat(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("loaded from context");
            }
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }
}
