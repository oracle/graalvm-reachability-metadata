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
    void loadWithLoaderThatRequiresHelperDefinitionDefinesHelperBeforeReportingMissingLibrary() {
        String libraryName = "metadata_forge_missing_native_library_helper_definition";
        ClassLoader parentLoader = NativeLibraryLoader$2Test.class.getClassLoader();
        Assertions.assertNotNull(parentLoader, "Expected an application class loader for netty-common tests");

        HelperDefiningClassLoader classLoader = new HelperDefiningClassLoader(parentLoader);
        UnsatisfiedLinkError error = Assertions.assertThrows(
                UnsatisfiedLinkError.class,
                () -> NativeLibraryLoader.load(libraryName, classLoader)
        );

        Assertions.assertTrue(
                error.getMessage().contains("could not load a native library"),
                () -> "Unexpected error message: " + error.getMessage()
        );
        Assertions.assertTrue(
                classLoader.deniedInitialHelperLoad(),
                "Expected helper lookup to fall back to defineClass"
        );

        Class<?> helperClass = classLoader.findDefinedHelperClass();
        Assertions.assertNotNull(
                helperClass,
                "Expected NativeLibraryLoader to define the helper class in the target loader"
        );
        Assertions.assertSame(classLoader, helperClass.getClassLoader());
        Assertions.assertEquals(
                List.of("META-INF/native/" + System.mapLibraryName(libraryName)),
                classLoader.requestedResources()
        );
    }

    private static final class HelperDefiningClassLoader extends ClassLoader {
        private static final String HELPER_CLASS_NAME = "io.netty.util.internal.NativeLibraryUtil";

        private boolean deniedInitialHelperLoad;
        private final List<String> requestedResources = new ArrayList<>();

        private HelperDefiningClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (HELPER_CLASS_NAME.equals(name)) {
                Class<?> loadedClass = findLoadedClass(name);
                if (loadedClass != null) {
                    if (resolve) {
                        resolveClass(loadedClass);
                    }
                    return loadedClass;
                }
                deniedInitialHelperLoad = true;
                throw new ClassNotFoundException(name);
            }
            return super.loadClass(name, resolve);
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            requestedResources.add(name);
            return getParent().getResources(name);
        }

        private boolean deniedInitialHelperLoad() {
            return deniedInitialHelperLoad;
        }

        private Class<?> findDefinedHelperClass() {
            return findLoadedClass(HELPER_CLASS_NAME);
        }

        private List<String> requestedResources() {
            return requestedResources;
        }
    }
}
