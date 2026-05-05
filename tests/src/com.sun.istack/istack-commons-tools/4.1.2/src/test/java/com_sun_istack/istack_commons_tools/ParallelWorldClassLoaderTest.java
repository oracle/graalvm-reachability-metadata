/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_istack.istack_commons_tools;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Enumeration;

import com.sun.istack.tools.ParallelWorldClassLoader;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class ParallelWorldClassLoaderTest {
    private static final String PREFIX = "parallel-world/";
    private static final String GENERATED_CLASS_NAME = "pw.GeneratedFixture";
    private static final String GENERATED_CLASS_RESOURCE = PREFIX + "pw/GeneratedFixture.class";
    private static final byte[] GENERATED_CLASS_BYTES = Base64.getDecoder().decode("""
            yv66vgAAAD0ACgcAAgEAE3B3L0dlbmVyYXRlZEZpeHR1cmUHAAQBABBqYXZhL2xhbmcvT2JqZWN0AQAGPGluaXQ+
            AQADKClWAQAEQ29kZQoAAwAJDAAFAAYAIQABAAMAAAAAAAEAAQAFAAYAAQAHAAAAEQABAAEAAAAFKrcACLEAAAAAAAA=
            """.replaceAll("\\s", ""));

    @Test
    void loadsClassBytesFromPrefixedParentResource(@TempDir Path root) throws Exception {
        writeFile(root, GENERATED_CLASS_RESOURCE, GENERATED_CLASS_BYTES);

        try (URLClassLoader parent = parentLoader(root);
                ParallelWorldClassLoader loader = new ParallelWorldClassLoader(parent, PREFIX)) {
            try {
                Class<?> loadedClass = loader.loadClass(GENERATED_CLASS_NAME);

                assertThat(loadedClass.getName()).isEqualTo(GENERATED_CLASS_NAME);
                assertThat(loadedClass.getClassLoader()).isSameAs(loader);
            } catch (Error error) {
                if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                    throw error;
                }
            }
        }
    }

    @Test
    void resolvesSingleResourceFromParallelWorldPrefix(@TempDir Path root) throws Exception {
        String resourceName = "config/settings.txt";
        String resourceText = "parallel resource";
        writeFile(root, PREFIX + resourceName, resourceText.getBytes(StandardCharsets.UTF_8));

        try (URLClassLoader parent = parentLoader(root);
                ParallelWorldClassLoader loader = new ParallelWorldClassLoader(parent, PREFIX)) {
            URL resource = loader.getResource(resourceName);

            assertThat(resource).isNotNull();
            assertThat(readText(resource)).isEqualTo(resourceText);
        }
    }

    @Test
    void queriesResourceEnumerationFromParallelWorldPrefix(@TempDir Path root) throws Exception {
        String resourceName = "config/listed.txt";
        writeFile(root, PREFIX + resourceName, "listed resource".getBytes(StandardCharsets.UTF_8));

        try (URLClassLoader parent = parentLoader(root);
                ParallelWorldClassLoader loader = new ParallelWorldClassLoader(parent, PREFIX)) {
            Enumeration<URL> resources = loader.getResources(resourceName);

            assertThat(resources).isNotNull();
        }
    }

    private static URLClassLoader parentLoader(Path root) throws IOException {
        return new URLClassLoader(new URL[] {root.toUri().toURL()}, ClassLoader.getPlatformClassLoader());
    }

    private static void writeFile(Path root, String name, byte[] bytes) throws IOException {
        Path file = root.resolve(name);
        Files.createDirectories(file.getParent());
        Files.write(file, bytes);
    }

    private static String readText(URL resource) throws IOException {
        try (InputStream input = resource.openStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
