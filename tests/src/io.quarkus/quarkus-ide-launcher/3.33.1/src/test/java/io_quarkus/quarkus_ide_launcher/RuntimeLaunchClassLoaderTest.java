/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_quarkus.quarkus_ide_launcher;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

import io.quarkus.launcher.RuntimeLaunchClassLoader;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class RuntimeLaunchClassLoaderTest {
    private static final String LOADED_CLASS_NAME = "io_quarkus.quarkus_ide_launcher.IdeDepsLoadedType";
    private static final String LOADED_CLASS_RESOURCE = "META-INF/ide-deps/io_quarkus/quarkus_ide_launcher/"
            + "IdeDepsLoadedType.class.ide-launcher-res";
    private static final byte[] LOADED_CLASS_BYTES = Base64.getDecoder().decode("""
            yv66vgAAAD0AEQoAAgADBwAEDAAFAAYBABBqYXZhL2xhbmcvT2JqZWN0AQAGPGluaXQ+
            AQADKClWCAAIAQAUbG9hZGVkIGZyb20gaWRlIGRlcHMHAAoBADFpb19xdWFya3VzL3F1
            YXJrdXNfaWRlX2xhdW5jaGVyL0lkZURlcHNMb2FkZWRUeXBlAQAEQ29kZQEAD0xpbmVO
            dW1iZXJUYWJsZQEAB21lc3NhZ2UBABQoKUxqYXZhL2xhbmcvU3RyaW5nOwEAClNvdXJj
            ZUZpbGUBABZJZGVEZXBzTG9hZGVkVHlwZS5qYXZhADEACQACAAAAAAACAAEABQAGAAEA
            CwAAAB0AAQABAAAABSq3AAGxAAAAAQAMAAAABgABAAAAAwABAA0ADgABAAsAAAAbAAEA
            AQAAAAMSB7AAAAABAAwAAAAGAAEAAAAFAAEADwAAAAIAEA==
            """.replace("\n", "").replace(" ", ""));

    @TempDir
    Path temporaryDirectory;

    @Test
    void findClassLoadsClassBytesFromParentIdeDependencyResource() throws Exception {
        TestRuntimeLaunchClassLoader loader = new TestRuntimeLaunchClassLoader(new IdeDependencyParentClassLoader(
                Map.of(),
                Map.of(),
                LOADED_CLASS_BYTES));

        try {
            Class<?> loadedClass = loader.exposedFindClass(LOADED_CLASS_NAME);

            assertThat(loadedClass.getName()).isEqualTo(LOADED_CLASS_NAME);
            assertThat(loadedClass.getClassLoader()).isSameAs(loader);
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    @Test
    void findResourceMapsRequestedNameToParentIdeDependencyResource() throws Exception {
        Path resourceFile = temporaryDirectory.resolve("application.properties.ide-launcher-res");
        Files.writeString(resourceFile, "quarkus.test=true");
        URL resourceUrl = resourceFile.toUri().toURL();
        TestRuntimeLaunchClassLoader loader = new TestRuntimeLaunchClassLoader(new IdeDependencyParentClassLoader(
                Map.of("META-INF/ide-deps/application.properties.ide-launcher-res", resourceUrl),
                Map.of(),
                LOADED_CLASS_BYTES));

        assertThat(loader.exposedFindResource("application.properties")).isEqualTo(resourceUrl);
    }

    @Test
    void findResourcesMapsRequestedNameToParentIdeDependencyResources() throws Exception {
        Path firstResourceFile = temporaryDirectory.resolve("first.properties.ide-launcher-res");
        Path secondResourceFile = temporaryDirectory.resolve("second.properties.ide-launcher-res");
        Files.writeString(firstResourceFile, "first=true");
        Files.writeString(secondResourceFile, "second=true");
        List<URL> resourceUrls = List.of(firstResourceFile.toUri().toURL(), secondResourceFile.toUri().toURL());
        TestRuntimeLaunchClassLoader loader = new TestRuntimeLaunchClassLoader(new IdeDependencyParentClassLoader(
                Map.of(),
                Map.of("META-INF/ide-deps/config/application.properties.ide-launcher-res", resourceUrls),
                LOADED_CLASS_BYTES));

        assertThat(Collections.list(loader.exposedFindResources("config/application.properties")))
                .containsExactlyElementsOf(resourceUrls);
    }

    private static final class TestRuntimeLaunchClassLoader extends RuntimeLaunchClassLoader {
        private TestRuntimeLaunchClassLoader(ClassLoader parent) {
            super(parent);
        }

        private Class<?> exposedFindClass(String name) throws ClassNotFoundException {
            return findClass(name);
        }

        private URL exposedFindResource(String name) {
            return findResource(name);
        }

        private Enumeration<URL> exposedFindResources(String name) throws IOException {
            return findResources(name);
        }
    }

    private static final class IdeDependencyParentClassLoader extends ClassLoader {
        private final Map<String, URL> resources;
        private final Map<String, List<URL>> resourceEnumerations;
        private final byte[] classBytes;

        private IdeDependencyParentClassLoader(
                Map<String, URL> resources,
                Map<String, List<URL>> resourceEnumerations,
                byte[] classBytes) {
            super(null);
            this.resources = resources;
            this.resourceEnumerations = resourceEnumerations;
            this.classBytes = classBytes.clone();
        }

        @Override
        public URL getResource(String name) {
            return resources.get(name);
        }

        @Override
        public Enumeration<URL> getResources(String name) {
            return Collections.enumeration(resourceEnumerations.getOrDefault(name, List.of()));
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            if (LOADED_CLASS_RESOURCE.equals(name)) {
                return new ByteArrayInputStream(classBytes);
            }
            return null;
        }
    }
}
