/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_conscrypt.conscrypt_openjdk_uber;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class NativeLibraryLoaderTest {

    private static final String CONSCRYPT_PACKAGE = "org.conscrypt.";
    private static final String NATIVE_LIBRARY_LOADER_CLASS_NAME = "org.conscrypt.NativeLibraryLoader";
    private static final String HIDDEN_HELPER_CLASS_NAME = "org.conscrypt.NativeLibraryUtil";
    private static final String LOAD_FIRST_AVAILABLE_METHOD_NAME = "loadFirstAvailable";
    private static final String PRIMARY_LIBRARY_RESOURCE_NAME = "META-INF/native/libconscrypt_openjdk_jni.so";
    private static final String OSX_FALLBACK_LIBRARY_RESOURCE_NAME = "META-INF/native/libconscrypt_openjdk_jni.jnilib";
    private static final String OSX_NAME = "Mac OS X";

    @TempDir
    Path tempDir;

    @Test
    void loadsFromOsxJnilibFallbackResourceWhenPrimaryResourceIsMissing() throws Exception {
        String previousOsName = System.getProperty("os.name");
        Path fallbackLibrary = tempDir.resolve("libconscrypt_openjdk_jni.jnilib");
        Files.write(fallbackLibrary, new byte[] {0x01, 0x23, 0x45, 0x67});

        System.setProperty("os.name", OSX_NAME);
        try (ChildFirstConscryptClassLoader isolatedConscryptClassLoader = new ChildFirstConscryptClassLoader(codeSourceUrl("org.conscrypt.Conscrypt"))) {
            NativeLibraryLookupClassLoader targetClassLoader = new NativeLibraryLookupClassLoader(fallbackLibrary.toUri().toURL(), true);
            List<Object> results = new ArrayList<>();

            boolean loaded = invokeLoadFirstAvailable(
                    isolatedConscryptClassLoader,
                    targetClassLoader,
                    results,
                    "conscrypt_openjdk_jni"
            );

            assertThat(loaded).isFalse();
            assertThat(results).isNotEmpty();
            assertThat(targetClassLoader.primaryResourceLookupCount).isEqualTo(1);
            assertThat(targetClassLoader.osxFallbackResourceLookupCount).isEqualTo(1);
            assertThat(targetClassLoader.helperClassLoadAttempts).isGreaterThan(0);
        } finally {
            restoreSystemProperty("os.name", previousOsName);
        }
    }

    @Test
    void readsHelperClassBytesWhenTargetLoaderCannotLoadNativeLibraryUtil() throws Exception {
        NativeLibraryLookupClassLoader targetClassLoader = new NativeLibraryLookupClassLoader(null, false);
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

    private static URL codeSourceUrl(String className) throws Exception {
        Class<?> type = Class.forName(className, false, NativeLibraryLoaderTest.class.getClassLoader());
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

    private static final class NativeLibraryLookupClassLoader extends ClassLoader {

        private final URL osxFallbackLibraryUrl;
        private final boolean exposeOsxFallbackResource;
        private int primaryResourceLookupCount;
        private int osxFallbackResourceLookupCount;
        private int helperClassLoadAttempts;

        private NativeLibraryLookupClassLoader(URL osxFallbackLibraryUrl, boolean exposeOsxFallbackResource) {
            super(ClassLoader.getPlatformClassLoader());
            this.osxFallbackLibraryUrl = osxFallbackLibraryUrl;
            this.exposeOsxFallbackResource = exposeOsxFallbackResource;
        }

        @Override
        public URL getResource(String name) {
            if (PRIMARY_LIBRARY_RESOURCE_NAME.equals(name)) {
                primaryResourceLookupCount++;
                return null;
            }
            if (exposeOsxFallbackResource && OSX_FALLBACK_LIBRARY_RESOURCE_NAME.equals(name)) {
                osxFallbackResourceLookupCount++;
                return osxFallbackLibraryUrl;
            }
            return null;
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

    private static final class ChildFirstConscryptClassLoader extends URLClassLoader {

        private ChildFirstConscryptClassLoader(URL jarUrl) {
            super(new URL[] {jarUrl}, ClassLoader.getPlatformClassLoader());
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> loadedClass = findLoadedClass(name);
                if (loadedClass == null) {
                    if (name.startsWith(CONSCRYPT_PACKAGE)) {
                        try {
                            loadedClass = findClass(name);
                        } catch (ClassNotFoundException ignored) {
                            loadedClass = super.loadClass(name, false);
                        }
                    } else {
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
}
