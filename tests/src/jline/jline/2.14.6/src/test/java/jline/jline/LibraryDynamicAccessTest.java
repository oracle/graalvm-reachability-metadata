/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jline.jline;

import org.fusesource.hawtjni.runtime.Library;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public final class LibraryDynamicAccessTest {

    private static final String MISSING_LIBRARY_NAME = "graalvm_reachability_metadata_missing_jline_native_library";

    @Test
    void loadConsultsConfiguredClassLoaderForNativeLibraryResources() {
        final RecordingResourceClassLoader classLoader = new RecordingResourceClassLoader(
            LibraryDynamicAccessTest.class.getClassLoader()
        );
        final Library library = new Library(MISSING_LIBRARY_NAME, null, classLoader);

        assertThrows(UnsatisfiedLinkError.class, library::load);

        final List<String> expectedResources = List.of(
            library.getPlatformSpecifcResourcePath(),
            library.getOperatingSystemSpecifcResourcePath(),
            library.getResorucePath()
        );
        assertEquals(expectedResources, classLoader.requestedResources);
    }

    private static final class RecordingResourceClassLoader extends ClassLoader {

        private final List<String> requestedResources = new ArrayList<>();

        private RecordingResourceClassLoader(final ClassLoader parent) {
            super(parent);
        }

        @Override
        public URL getResource(final String name) {
            this.requestedResources.add(name);
            return null;
        }
    }
}
