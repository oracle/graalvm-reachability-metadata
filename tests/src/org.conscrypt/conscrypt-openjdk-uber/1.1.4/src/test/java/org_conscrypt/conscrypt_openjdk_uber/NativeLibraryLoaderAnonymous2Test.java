/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_conscrypt.conscrypt_openjdk_uber;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class NativeLibraryLoaderAnonymous2Test {
    private static final String NATIVE_LIBRARY_LOADER_CLASS_NAME =
            "org.conscrypt.NativeLibraryLoader";
    private static final String NATIVE_LIBRARY_UTIL_CLASS_NAME =
            "org.conscrypt.NativeLibraryUtil";
    private static final String MISSING_LIBRARY_NAME =
            "conscrypt_loader_anonymous2_define_class_missing";

    @Test
    void loadFirstAvailableDefinesNativeLibraryUtilInTargetClassloader() throws Exception {
        try {
            exerciseNativeLibraryUtilDefinitionPath();
        } catch (InvocationTargetException exception) {
            if (!isUnsupportedFeatureError(exception.getCause())) {
                throw exception;
            }
        } catch (Error error) {
            if (!isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static boolean isUnsupportedFeatureError(Throwable error) {
        return error instanceof Error
                && NativeImageSupport.isUnsupportedFeatureError((Error) error);
    }

    private static void exerciseNativeLibraryUtilDefinitionPath() throws Exception {
        Class<?> nativeLibraryLoader = Class.forName(NATIVE_LIBRARY_LOADER_CLASS_NAME);
        Method loadFirstAvailable = nativeLibraryLoader.getDeclaredMethod(
                "loadFirstAvailable", ClassLoader.class, List.class, String[].class);
        loadFirstAvailable.setAccessible(true);

        NativeLibraryUtilMissingClassLoader targetClassLoader =
                new NativeLibraryUtilMissingClassLoader();
        List<Object> loadResults = new ArrayList<>();
        boolean loaded = (Boolean) loadFirstAvailable.invoke(
                null, targetClassLoader, loadResults, new String[] {MISSING_LIBRARY_NAME});

        assertThat(loaded).isFalse();
        assertThat(loadResults).isNotEmpty();
        assertThat(targetClassLoader.getClassLoadAttempts())
                .contains(NATIVE_LIBRARY_UTIL_CLASS_NAME);
    }

    private static final class NativeLibraryUtilMissingClassLoader extends ClassLoader {
        private final List<String> classLoadAttempts = new ArrayList<>();

        private NativeLibraryUtilMissingClassLoader() {
            super(null);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            classLoadAttempts.add(name);
            if (NATIVE_LIBRARY_UTIL_CLASS_NAME.equals(name)) {
                throw new ClassNotFoundException(name);
            }
            return super.loadClass(name);
        }

        private List<String> getClassLoadAttempts() {
            return classLoadAttempts;
        }
    }
}
