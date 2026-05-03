/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_fusesource_hawtjni.hawtjni_runtime;

import org.fusesource.hawtjni.runtime.Library;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class LibraryTest {
    private static final String MISSING_LIBRARY_NAME = "hawtjni_runtime_coverage_probe_missing";

    @Test
    public void loadQueriesClasspathNativeLibraryResourcesWhenSystemLoadFails() {
        RecordingClassLoader classLoader = new RecordingClassLoader();
        Library library = new Library(MISSING_LIBRARY_NAME, null, classLoader);

        UnsatisfiedLinkError error = assertThrows(UnsatisfiedLinkError.class, library::load);

        assertThat(error).hasMessageContaining("Could not load library");
        assertThat(classLoader.resourceNames()).containsExactly(
                library.getPlatformSpecifcResourcePath(),
                library.getOperatingSystemSpecifcResourcePath(),
                library.getResorucePath());
    }

    private static final class RecordingClassLoader extends ClassLoader {
        private final List<String> resourceNames = new ArrayList<>();

        private RecordingClassLoader() {
            super(null);
        }

        @Override
        public URL getResource(String name) {
            resourceNames.add(name);
            return null;
        }

        private List<String> resourceNames() {
            return resourceNames;
        }
    }
}
