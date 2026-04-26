/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_common;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import io.netty.util.internal.NativeLibraryLoader;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class NativeLibraryLoader$2Test {
    @Test
    void loadWithLoaderThatResolvesTheHelperInvokesTheHelperMethodBeforeReportingMissingLibrary() {
        String libraryName = "metadata_forge_missing_native_library_helper_invocation";
        ClassLoader parentLoader = NativeLibraryLoader$2Test.class.getClassLoader();
        Assertions.assertNotNull(parentLoader, "Expected an application class loader for netty-common tests");

        HelperResolvingClassLoader classLoader = new HelperResolvingClassLoader(parentLoader);
        UnsatisfiedLinkError error = Assertions.assertThrows(
                UnsatisfiedLinkError.class,
                () -> NativeLibraryLoader.load(libraryName, classLoader)
        );

        Assertions.assertTrue(
                error.getMessage().contains("could not load a native library"),
                () -> "Unexpected error message: " + error.getMessage()
        );
        Assertions.assertTrue(classLoader.requestedHelperClass(), "Expected helper class lookup before fallback");
        Assertions.assertEquals(
                List.of("META-INF/native/" + System.mapLibraryName(libraryName)),
                classLoader.requestedResources()
        );
    }

    private static final class HelperResolvingClassLoader extends ClassLoader {
        private static final String HELPER_CLASS_NAME = "io.netty.util.internal.NativeLibraryUtil";

        private boolean requestedHelperClass;
        private final List<String> requestedResources = new ArrayList<>();

        private HelperResolvingClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (HELPER_CLASS_NAME.equals(name)) {
                requestedHelperClass = true;
            }
            return super.loadClass(name, resolve);
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            requestedResources.add(name);
            return getParent().getResources(name);
        }

        private boolean requestedHelperClass() {
            return requestedHelperClass;
        }

        private List<String> requestedResources() {
            return requestedResources;
        }
    }
}
