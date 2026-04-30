/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_conscrypt.conscrypt_openjdk_uber;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class NativeLibraryLoaderTest {
    private static final String NATIVE_LIBRARY_LOADER = "org.conscrypt.NativeLibraryLoader";
    private static final String NATIVE_LIBRARY_UTIL = "org.conscrypt.NativeLibraryUtil";
    private static final Set<String> ISOLATED_CONSCRYPT_CLASSES = Set.of(
            NATIVE_LIBRARY_LOADER,
            "org.conscrypt.NativeLibraryLoader$LoadResult",
            "org.conscrypt.NativeLibraryLoader$1",
            "org.conscrypt.NativeLibraryLoader$2",
            NATIVE_LIBRARY_UTIL,
            "org.conscrypt.HostProperties",
            "org.conscrypt.HostProperties$OperatingSystem",
            "org.conscrypt.HostProperties$Architecture",
            "org.conscrypt.Platform");

    @Test
    void classToByteArrayReadsNativeLibraryUtilClassResource() throws Exception {
        Class<?> loaderClass = Class.forName(NATIVE_LIBRARY_LOADER);
        Class<?> utilityClass = Class.forName(NATIVE_LIBRARY_UTIL);
        Method classToByteArray = loaderClass.getDeclaredMethod("classToByteArray", Class.class);
        classToByteArray.setAccessible(true);

        byte[] classBytes = (byte[]) classToByteArray.invoke(null, utilityClass);

        assertTrue(classBytes.length > 4);
        assertArrayEquals(new byte[] {(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE},
                Arrays.copyOf(classBytes, 4));
    }

    @Test
    void loadFirstAvailableQueriesNativeResourceNamesFromProvidedClassLoader()
            throws Exception {
        ResourceRecordingClassLoader resourceLoader = new ResourceRecordingClassLoader(
                NativeLibraryLoaderTest.class.getClassLoader());
        List<Object> loadResults = new ArrayList<>();
        Class<?> loaderClass = loadIsolatedLoaderClass();
        Method loadFirstAvailable = loaderClass.getDeclaredMethod(
                "loadFirstAvailable", ClassLoader.class, List.class, String[].class);
        loadFirstAvailable.setAccessible(true);

        boolean loaded = (boolean) loadFirstAvailable.invoke(null, resourceLoader, loadResults,
                new String[] {"definitely_missing_conscrypt_loader_test"});

        assertFalse(loaded);
        assertFalse(loadResults.isEmpty());
        assertTrue(resourceLoader.requestedResources.stream()
                .anyMatch(resource -> resource.startsWith("META-INF/native/")));
    }

    private static Class<?> loadIsolatedLoaderClass() throws Exception {
        String previousOsName = System.getProperty("os.name");
        IsolatedConscryptClassLoader classLoader = new IsolatedConscryptClassLoader(
                NativeLibraryLoaderTest.class.getClassLoader());
        try {
            System.setProperty("os.name", "Mac OS X");
            return Class.forName(NATIVE_LIBRARY_LOADER, true, classLoader);
        } catch (UnsupportedOperationException | LinkageError ex) {
            return Class.forName(NATIVE_LIBRARY_LOADER);
        } finally {
            restoreOsName(previousOsName);
        }
    }

    private static void restoreOsName(String previousOsName) {
        if (previousOsName == null) {
            System.clearProperty("os.name");
        } else {
            System.setProperty("os.name", previousOsName);
        }
    }

    private static final class ResourceRecordingClassLoader extends ClassLoader {
        private final List<String> requestedResources = new ArrayList<>();

        private ResourceRecordingClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public URL getResource(String name) {
            requestedResources.add(name);
            return null;
        }
    }

    private static final class IsolatedConscryptClassLoader extends ClassLoader {
        private final Set<String> definedClasses = new HashSet<>();

        private IsolatedConscryptClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> loadedClass = findLoadedClass(name);
                if (loadedClass == null) {
                    loadedClass = ISOLATED_CONSCRYPT_CLASSES.contains(name)
                            ? findClass(name) : super.loadClass(name, false);
                }
                if (resolve) {
                    resolveClass(loadedClass);
                }
                return loadedClass;
            }
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (!definedClasses.add(name)) {
                throw new ClassNotFoundException(name);
            }
            String resourceName = name.replace('.', '/') + ".class";
            try {
                byte[] classBytes = readResourceBytes(resourceName);
                return defineClass(name, classBytes, 0, classBytes.length);
            } catch (IOException ex) {
                throw new ClassNotFoundException(name, ex);
            }
        }

        private byte[] readResourceBytes(String resourceName) throws IOException {
            try (InputStream input = getParent().getResourceAsStream(resourceName)) {
                if (input == null) {
                    throw new IOException("Missing class resource: " + resourceName);
                }
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                for (int read = input.read(buffer); read != -1; read = input.read(buffer)) {
                    output.write(buffer, 0, read);
                }
                return output.toByteArray();
            }
        }
    }
}
