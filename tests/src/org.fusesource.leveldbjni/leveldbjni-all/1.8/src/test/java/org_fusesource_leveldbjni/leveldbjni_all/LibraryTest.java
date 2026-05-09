/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_fusesource_leveldbjni.leveldbjni_all;

import org.fusesource.hawtjni.runtime.Library;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class LibraryTest {
    @Test
    void loadSearchesClasspathResourcesWhenLibraryIsNotOnLibraryPath() {
        String libraryName = "leveldbjni_coverage_probe";
        ClassLoader classLoader = LibraryTest.class.getClassLoader();
        Library library = new Library(libraryName, "coverage", classLoader);

        assertThatThrownBy(library::load)
                .isInstanceOf(UnsatisfiedLinkError.class)
                .hasMessageContaining("Could not load library");
    }
}
