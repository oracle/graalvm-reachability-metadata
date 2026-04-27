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
import java.util.Base64;
import org.apache.tools.ant.AntClassLoader;
import org.apache.tools.ant.Project;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class AntClassLoaderTest {
    private static final String FIXTURE_CLASS_NAME = "fixtures.AntLoadedFixture";
    private static final String FIXTURE_CLASS_RESOURCE = "fixtures/AntLoadedFixture.class";
    private static final String FIXTURE_CLASS_BYTES = "yv66vgAAADQAEQoAAgADBwAEDAAFAAYBABBqYXZhL2xhbmcv"
            + "T2JqZWN0AQAGPGluaXQ+AQADKClWCAAIAQAGbG9hZGVkBwAKAQAZZml4dHVyZXMv"
            + "QW50TG9hZGVkRml4dHVyZQEABENvZG"
            + "UBAA9MaW5lTnVtYmVyVGFibGUBAAdtZXNzYWdlAQAUKClMamF2YS9sYW5nL1N0cmluZzsBAApT"
            + "b3VyY2VGaWxlAQAVQW50TG9hZGVkRml4dHVyZS5qYXZhACEACQACAAAAAAACAAEABQAGAAEACw"
            + "AAAB0AAQABAAAABSq3AAGxAAAAAQAMAAAABgABAAAAAgABAA0ADgABAAsAAAAbAAEAAQAAAAMS"
            + "B7AAAAABAAwAAAAGAAEAAAAEAAEADwAAAAIAEA==";

    @Test
    void exercisesClassInitializationAndClassLoading(@TempDir Path temporaryDirectory) throws Exception {
        AntClassLoader.initializeClass(Project.class);

        Path classesDirectory = temporaryDirectory.resolve("classes");
        Path fixtureClassFile = classesDirectory.resolve(FIXTURE_CLASS_RESOURCE);
        Files.createDirectories(fixtureClassFile.getParent());
        Files.write(fixtureClassFile, Base64.getDecoder().decode(FIXTURE_CLASS_BYTES));

        AntClassLoader loader = new AntClassLoader();
        loader.addPathElement(classesDirectory.toString());
        try {
            Class<?> loadedClass = loader.forceLoadClass(FIXTURE_CLASS_NAME);
            assertThat(loadedClass.getName()).isEqualTo(FIXTURE_CLASS_NAME);
        } catch (ClassNotFoundException ex) {
            assertThat(ex).hasMessage(FIXTURE_CLASS_NAME);
        }

        assertThat(loader.forceLoadSystemClass(String.class.getName())).isSameAs(String.class);
        assertThat(loader.loadClass(Integer.class.getName())).isSameAs(Integer.class);
        loader.cleanup();
    }

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
    }

    private static String read(InputStream inputStream) throws IOException {
        assertThat(inputStream).isNotNull();
        try (InputStream stream = inputStream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
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
