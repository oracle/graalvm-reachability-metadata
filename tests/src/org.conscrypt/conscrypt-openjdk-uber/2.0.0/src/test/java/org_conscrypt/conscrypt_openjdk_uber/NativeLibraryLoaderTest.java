/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_conscrypt.conscrypt_openjdk_uber;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class NativeLibraryLoaderTest {

    private static final String NATIVE_LIBRARY_LOADER_CLASS_NAME = "org.conscrypt.NativeLibraryLoader";
    private static final String HIDDEN_HELPER_CLASS_NAME = "org.conscrypt.NativeLibraryUtil";
    private static final String LOAD_FIRST_AVAILABLE_METHOD_NAME = "loadFirstAvailable";

    @Test
    void readsHelperClassBytesWhenTargetLoaderCannotLoadNativeLibraryUtil() throws Exception {
        NativeLibraryLookupClassLoader targetClassLoader = new NativeLibraryLookupClassLoader();
        List<Object> results = new ArrayList<>();

        boolean loaded = invokeLoadFirstAvailable(
                NativeLibraryLoaderTest.class.getClassLoader(),
                targetClassLoader,
                results,
                "conscrypt-missing-native-library"
        );

        assertThat(loaded).isFalse();
        assertThat(results).isNotEmpty();
        assertThat(targetClassLoader.helperClassLoadAttempts).isGreaterThan(0);
    }

    private static boolean invokeLoadFirstAvailable(
            ClassLoader conscryptClassLoader,
            ClassLoader targetClassLoader,
            List<Object> results,
            String... libraryNames) throws Exception {
        Class<?> nativeLibraryLoaderClass = Class.forName(NATIVE_LIBRARY_LOADER_CLASS_NAME, true, conscryptClassLoader);
        Method loadFirstAvailable = nativeLibraryLoaderClass.getDeclaredMethod(
                LOAD_FIRST_AVAILABLE_METHOD_NAME,
                ClassLoader.class,
                List.class,
                String[].class
        );
        loadFirstAvailable.setAccessible(true);
        try {
            return (boolean) loadFirstAvailable.invoke(null, targetClassLoader, results, (Object) libraryNames);
        } catch (InvocationTargetException invocationTargetException) {
            Throwable targetException = invocationTargetException.getTargetException();
            if (targetException instanceof Exception exception) {
                throw exception;
            }
            if (targetException instanceof Error error) {
                throw error;
            }
            throw invocationTargetException;
        }
    }

    private static final class NativeLibraryLookupClassLoader extends ClassLoader {

        private int helperClassLoadAttempts;

        private NativeLibraryLookupClassLoader() {
            super(ClassLoader.getPlatformClassLoader());
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (HIDDEN_HELPER_CLASS_NAME.equals(name)) {
                helperClassLoadAttempts++;
                throw new ClassNotFoundException(name);
            }
            return super.loadClass(name, resolve);
        }
    }
}
