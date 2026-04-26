/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_common;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import io.netty.util.internal.NativeLibraryLoader;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class NativeLibraryLoaderTest {
    @Test
    void loadWithNullLoaderFallsBackToSystemResourceLookupWhenLibraryIsMissing() {
        UnsatisfiedLinkError error = Assertions.assertThrows(
                UnsatisfiedLinkError.class,
                () -> NativeLibraryLoader.load("metadata_forge_missing_native_library_system_lookup", null)
        );

        Assertions.assertTrue(
                error.getMessage().contains("could not load a native library"),
                () -> "Unexpected error message: " + error.getMessage()
        );
    }

    @Test
    void loadWithCustomLoaderSearchesLoaderResourcesWhenLibraryIsMissing() {
        String libraryName = "metadata_forge_missing_native_library_custom_lookup";
        RecordingClassLoader classLoader = new RecordingClassLoader();

        UnsatisfiedLinkError error = Assertions.assertThrows(
                UnsatisfiedLinkError.class,
                () -> NativeLibraryLoader.load(libraryName, classLoader)
        );

        Assertions.assertTrue(
                error.getMessage().contains("could not load a native library"),
                () -> "Unexpected error message: " + error.getMessage()
        );
        Assertions.assertEquals(
                List.of("META-INF/native/" + System.mapLibraryName(libraryName)),
                classLoader.requestedResources()
        );
    }

    @Test
    void loadWithIsolatedNettyClassLoaderFindsHelperClassBeforeFallingBackToMissingLibraryResource() throws Exception {
        try (URLClassLoader classLoader = new URLClassLoader(new URL[] {findNettyCommonLocation()}, null)) {
            UnsatisfiedLinkError error = Assertions.assertThrows(
                    UnsatisfiedLinkError.class,
                    () -> NativeLibraryLoader.load("metadata_forge_missing_native_library_helper_lookup", classLoader)
            );

            Assertions.assertTrue(
                    error.getMessage().contains("could not load a native library"),
                    () -> "Unexpected error message: " + error.getMessage()
            );
        }
    }

    private static URL findNettyCommonLocation() throws MalformedURLException, URISyntaxException {
        String resourceName = "io/netty/util/internal/NativeLibraryUtil.class";
        URL resource = NativeLibraryLoaderTest.class.getClassLoader().getResource(resourceName);
        Assertions.assertNotNull(resource, "Expected netty-common on the test classpath");

        if ("jar".equals(resource.getProtocol())) {
            String externalForm = resource.toExternalForm();
            int separatorIndex = externalForm.indexOf("!/");
            return URI.create(externalForm.substring("jar:".length(), separatorIndex)).toURL();
        }

        if ("file".equals(resource.getProtocol())) {
            String externalForm = resource.toExternalForm();
            return URI.create(externalForm.substring(0, externalForm.length() - resourceName.length())).toURL();
        }

        throw new IllegalStateException("Unsupported netty-common location: " + resource);
    }

    private static final class RecordingClassLoader extends ClassLoader {
        private final List<String> requestedResources = new ArrayList<>();

        private RecordingClassLoader() {
            super(null);
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            requestedResources.add(name);
            return Collections.emptyEnumeration();
        }

        private List<String> requestedResources() {
            return requestedResources;
        }
    }
}
