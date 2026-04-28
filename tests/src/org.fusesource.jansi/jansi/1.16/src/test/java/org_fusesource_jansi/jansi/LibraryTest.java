/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_fusesource_jansi.jansi;

import org.fusesource.hawtjni.runtime.Library;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class LibraryTest {

    private static final String MISSING_LIBRARY_NAME = "graalvmjansimissingnative";

    @Test
    void loadChecksBundledNativeLibraryResourcesWhenNativeLibraryLookupFails() {
        TrackingResourceClassLoader classLoader = new TrackingResourceClassLoader(LibraryTest.class.getClassLoader());
        Library library = new Library(MISSING_LIBRARY_NAME, "integration-test", classLoader);

        assertThatThrownBy(library::load)
                .isInstanceOf(UnsatisfiedLinkError.class)
                .hasMessageContaining("Could not load library. Reasons:");

        assertThat(classLoader.requestedResources).containsExactly(
                library.getPlatformSpecifcResourcePath(),
                library.getOperatingSystemSpecifcResourcePath(),
                library.getResorucePath());
    }

    public static final class TrackingResourceClassLoader extends ClassLoader {

        private final List<String> requestedResources = new ArrayList<String>();

        public TrackingResourceClassLoader(final ClassLoader parent) {
            super(parent);
        }

        @Override
        public URL getResource(final String name) {
            requestedResources.add(name);
            return null;
        }
    }
}
