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
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.List;
import org.conscrypt.Conscrypt;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class NativeLibraryLoaderTest {
    private static final String NATIVE_LIBRARY_LOADER_CLASS_NAME =
            "org.conscrypt.NativeLibraryLoader";
    private static final String NATIVE_LIBRARY_UTIL_CLASS_NAME =
            "org.conscrypt.NativeLibraryUtil";
    private static final String MAC_OS_NAME = "Mac OS X";
    private static final String OS_NAME_PROPERTY = "os.name";
    private static final String MISSING_LIBRARY_NAME = "conscrypt_loader_coverage_missing";
    private static final String EXPECTED_MAC_FALLBACK_RESOURCE = "META-INF/native/lib"
            + MISSING_LIBRARY_NAME + ".jnilib";

    @Test
    void loadFirstAvailableChecksNativeResourcesAndHelperClassBytes() throws Exception {
        try {
            exerciseLoaderInIsolatedConscryptClasses();
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

    private static void exerciseLoaderInIsolatedConscryptClasses() throws Exception {
        String previousOsName = System.getProperty(OS_NAME_PROPERTY);
        System.setProperty(OS_NAME_PROPERTY, MAC_OS_NAME);

        try (ChildFirstConscryptClassLoader conscryptClasses = new ChildFirstConscryptClassLoader(
                new URL[] {codeSourceUrl(Conscrypt.class)},
                NativeLibraryLoaderTest.class.getClassLoader())) {
            Class<?> nativeLibraryLoader = Class.forName(
                    NATIVE_LIBRARY_LOADER_CLASS_NAME, true, conscryptClasses);
            Method loadFirstAvailable = nativeLibraryLoader.getDeclaredMethod(
                    "loadFirstAvailable", ClassLoader.class, List.class, String[].class);
            loadFirstAvailable.setAccessible(true);

            TrackingClassLoader targetClassLoader = new TrackingClassLoader();
            List<Object> loadResults = new ArrayList<>();
            boolean loaded = (Boolean) loadFirstAvailable.invoke(
                    null, targetClassLoader, loadResults, new String[] {MISSING_LIBRARY_NAME});

            assertThat(loaded).isFalse();
            assertThat(loadResults).isNotEmpty();
            assertThat(targetClassLoader.getClassLoadAttempts())
                    .contains(NATIVE_LIBRARY_UTIL_CLASS_NAME);
            assertThat(targetClassLoader.getRequestedResources())
                    .anyMatch(resource -> resource.startsWith(
                            "META-INF/native/lib" + MISSING_LIBRARY_NAME))
                    .contains(EXPECTED_MAC_FALLBACK_RESOURCE);
        } finally {
            restoreSystemProperty(OS_NAME_PROPERTY, previousOsName);
        }
    }

    private static URL codeSourceUrl(Class<?> type) {
        CodeSource codeSource = type.getProtectionDomain().getCodeSource();

        assertThat(codeSource).isNotNull();
        return codeSource.getLocation();
    }

    private static void restoreSystemProperty(String name, String previousValue) {
        if (previousValue == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, previousValue);
        }
    }

    private static final class ChildFirstConscryptClassLoader extends URLClassLoader {

        private ChildFirstConscryptClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.startsWith("org.conscrypt.")) {
                return super.loadClass(name, resolve);
            }

            synchronized (getClassLoadingLock(name)) {
                Class<?> loadedClass = findLoadedClass(name);
                if (loadedClass == null) {
                    try {
                        loadedClass = findClass(name);
                    } catch (ClassNotFoundException ignored) {
                        loadedClass = super.loadClass(name, false);
                    }
                }
                if (resolve) {
                    resolveClass(loadedClass);
                }
                return loadedClass;
            }
        }
    }

    private static final class TrackingClassLoader extends ClassLoader {
        private final List<String> classLoadAttempts = new ArrayList<>();
        private final List<String> requestedResources = new ArrayList<>();

        private TrackingClassLoader() {
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

        @Override
        public URL getResource(String name) {
            requestedResources.add(name);
            return null;
        }

        private List<String> getClassLoadAttempts() {
            return classLoadAttempts;
        }

        private List<String> getRequestedResources() {
            return requestedResources;
        }
    }
}
