/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_woodstox.woodstox_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctc.wstx.shaded.msv.relaxng_datatype.DatatypeLibrary;
import com.ctc.wstx.shaded.msv.relaxng_datatype.helpers.DatatypeLibraryLoader;
import java.net.URL;
import java.net.URLClassLoader;
import org.junit.jupiter.api.Test;

public class DatatypeLibraryLoaderServiceLoader2DynamicAccessTest {
    @Test
    void usesTheContextClassLoaderWhenResolvingServiceProviders() throws Exception {
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        ClassLoader parent = DatatypeLibraryLoaderServiceLoader2DynamicAccessTest.class.getClassLoader();
        try (URLClassLoader contextLoader = new URLClassLoader(new URL[0], parent)) {
            Thread.currentThread().setContextClassLoader(contextLoader);

            DatatypeLibrary library = new DatatypeLibraryLoader()
                    .createDatatypeLibrary(DatatypeLibraryLoaderServiceDynamicAccessTest.SERVICE_URI);

            assertThat(library).isNotNull();
            assertThat(library.createDatatype("token").isValid("loader2", null)).isTrue();
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
    }
}
