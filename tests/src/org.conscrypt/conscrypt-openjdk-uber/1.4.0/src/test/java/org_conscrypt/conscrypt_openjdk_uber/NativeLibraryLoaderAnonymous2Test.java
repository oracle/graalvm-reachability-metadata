/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_conscrypt.conscrypt_openjdk_uber;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

public class NativeLibraryLoaderAnonymous2Test {
    private static final String NATIVE_LIBRARY_LOADER = "org.conscrypt.NativeLibraryLoader";
    private static final String NATIVE_LIBRARY_UTIL = "org.conscrypt.NativeLibraryUtil";

    @Test
    void loadFirstAvailableDefinesHelperClassWhenTargetLoaderCannotLoadIt() throws Exception {
        HelperDefiningClassLoader targetLoader = new HelperDefiningClassLoader(
                NativeLibraryLoaderAnonymous2Test.class.getClassLoader());
        List<Object> loadResults = new ArrayList<>();

        boolean loaded = (boolean) loadFirstAvailableMethod().invoke(null, targetLoader,
                loadResults, new String[] {"definitely_missing_conscrypt_helper_loader_test"});

        assertFalse(loaded);
        assertFalse(loadResults.isEmpty());
        assertTrue(targetLoader.deniedNativeLibraryUtilLoad());
    }

    private static Method loadFirstAvailableMethod() throws Exception {
        Class<?> loaderClass = Class.forName(NATIVE_LIBRARY_LOADER);
        Method loadFirstAvailable = loaderClass.getDeclaredMethod(
                "loadFirstAvailable", ClassLoader.class, List.class, String[].class);
        loadFirstAvailable.setAccessible(true);
        return loadFirstAvailable;
    }

    private static final class HelperDefiningClassLoader extends ClassLoader {
        private boolean deniedNativeLibraryUtilLoad;

        private HelperDefiningClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (NATIVE_LIBRARY_UTIL.equals(name)) {
                deniedNativeLibraryUtilLoad = true;
                throw new ClassNotFoundException(name);
            }
            return super.loadClass(name, resolve);
        }

        @Override
        public URL getResource(String name) {
            return null;
        }

        private boolean deniedNativeLibraryUtilLoad() {
            return deniedNativeLibraryUtilLoad;
        }
    }
}
