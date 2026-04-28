/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_fusesource_hawtjni.hawtjni_runtime;

import org.fusesource.hawtjni.runtime.Library;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class LibraryTest {
    private static final String MISSING_LIBRARY_NAME = "graalvm_reachability_metadata_missing_hawtjni_library";

    @Test
    void loadSearchesClasspathResourcesBeforeReportingMissingNativeLibrary() {
        ClassLoader classLoader = LibraryTest.class.getClassLoader();
        Library library = new Library(MISSING_LIBRARY_NAME, null, classLoader);

        assertThatExceptionOfType(UnsatisfiedLinkError.class)
                .isThrownBy(library::load)
                .withMessageContaining("Could not load library");
    }

    @Test
    void resourcePathsUseMappedLibraryName() {
        Library library = new Library(MISSING_LIBRARY_NAME, null, LibraryTest.class.getClassLoader());
        String libraryFileName = library.getLibraryFileName();

        assertThat(library.getPlatformSpecifcResourcePath())
                .isEqualTo("META-INF/native/" + Library.getPlatform() + "/" + libraryFileName);
        assertThat(library.getOperatingSystemSpecifcResourcePath())
                .isEqualTo("META-INF/native/" + Library.getOperatingSystem() + "/" + libraryFileName);
        assertThat(library.getResorucePath()).isEqualTo("META-INF/native/" + libraryFileName);
    }
}
