/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.tools.ant.AntClassLoader;
import org.apache.tools.ant.Project;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class AntClassLoaderTest {
    @Test
    void delegatesResourcesToParentBeforeAndAfterLoaderClasspath(@TempDir Path temporaryDirectory) throws Exception {
        String resourceName = "ant-class-loader-parent-resource.txt";
        String resourceContent = "parent resource";
        Path resourceFile = temporaryDirectory.resolve(resourceName);
        Files.write(resourceFile, resourceContent.getBytes(StandardCharsets.UTF_8));

        URL resourceUrl = resourceFile.toUri().toURL();
        ParentResourceClassLoader parentLoader = new ParentResourceClassLoader(resourceName, resourceUrl,
                resourceContent);
        AntClassLoader parentFirstLoader = new AntClassLoader(parentLoader, true);
        AntClassLoader loaderFirstLoader = new AntClassLoader(parentLoader, false);

        assertThat(parentFirstLoader.getResource(resourceName)).isEqualTo(resourceUrl);
        assertThat(read(parentFirstLoader.getResourceAsStream(resourceName))).isEqualTo(resourceContent);
        assertThat(loaderFirstLoader.getResource(resourceName)).isEqualTo(resourceUrl);

        parentFirstLoader.cleanup();
        loaderFirstLoader.cleanup();

        NoParentAntClassLoader noParentLoader = new NoParentAntClassLoader();
        assertThat(read(noParentLoader.getResourceAsStream("org/apache/tools/ant/Project.class"))).isNotEmpty();
        noParentLoader.cleanup();
    }

    private static String read(InputStream inputStream) throws IOException {
        assertThat(inputStream).isNotNull();
        try (InputStream stream = inputStream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static final class NoParentAntClassLoader extends AntClassLoader {
        private NoParentAntClassLoader() {
            super(null, true);
        }

        @Override
        public void setParent(ClassLoader parent) {
            // Keep AntClassLoader's private parent field null to exercise the system-loader fallback.
        }
    }

    private static final class ParentResourceClassLoader extends ClassLoader {
        private final String resourceName;
        private final URL resourceUrl;
        private final byte[] resourceBytes;

        private ParentResourceClassLoader(String resourceName, URL resourceUrl, String resourceContent) {
            this.resourceName = resourceName;
            this.resourceUrl = resourceUrl;
            this.resourceBytes = resourceContent.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public URL getResource(String name) {
            if (resourceName.equals(name)) {
                return resourceUrl;
            }
            return super.getResource(name);
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            if (resourceName.equals(name)) {
                return new ByteArrayInputStream(resourceBytes);
            }
            return super.getResourceAsStream(name);
        }
    }
}
