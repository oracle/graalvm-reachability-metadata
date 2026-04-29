/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_fusesource_hawtjni.hawtjni_runtime;

import org.fusesource.hawtjni.runtime.Library;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LibraryTest {
    @Test
    void loadSearchesClasspathResourcesWhenNativeLibraryIsUnavailable() {
        ClassLoader classLoader = LibraryTest.class.getClassLoader();
        assertNotNull(classLoader);

        Library library = new Library("hawtjni_runtime_test_missing_library", null, classLoader);

        UnsatisfiedLinkError error = assertThrows(UnsatisfiedLinkError.class, library::load);
        assertTrue(error.getMessage().contains("Could not load library"));
    }
}
