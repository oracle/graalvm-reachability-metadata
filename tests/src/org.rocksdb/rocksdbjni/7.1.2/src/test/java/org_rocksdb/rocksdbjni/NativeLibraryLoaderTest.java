/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_rocksdb.rocksdbjni;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rocksdb.NativeLibraryLoader;
import org.rocksdb.util.Environment;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class NativeLibraryLoaderTest {
    private static final String ROCKSDB_PACKAGE = "org.rocksdb.";
    private static final String ROCKSDB_LIBRARY_NAME = "rocksdb";
    private static final String SIMULATED_MAC_OS = "mac os x";
    private static final String SIMULATED_MAC_ARCH = "x86_64";

    @TempDir
    Path tempDir;

    @Test
    void loadLibraryFromJarToTempExtractsTheCurrentPlatformLibrary() throws Throwable {
        File extractedLibrary = invokeLoadLibraryFromJarToTemp(
                NativeLibraryLoader.getInstance(),
                tempDir.toString()
        );

        assertThat(extractedLibrary.toPath()).isRegularFile();
        assertThat(extractedLibrary.getName()).isEqualTo(Environment.getJniLibraryFileName(ROCKSDB_LIBRARY_NAME));
        assertThat(Files.size(extractedLibrary.toPath())).isGreaterThan(0L);
    }

    @Test
    void loadLibraryFromJarToTempAttemptsFallbackLookupWhenPrimaryResourceIsMissing() throws Throwable {
        org.junit.jupiter.api.Assumptions.assumeFalse(isNativeImageRuntime());

        URL rocksDbJar = NativeLibraryLoader.class.getProtectionDomain().getCodeSource().getLocation();

        try (ResourceHidingClassLoader classLoader = new ResourceHidingClassLoader(
                rocksDbJar,
                "librocksdbjni-osx-x86_64.jnilib"
        )) {
            Class<?> environmentClass = Class.forName("org.rocksdb.util.Environment", true, classLoader);
            setStaticField(environmentClass, "OS", SIMULATED_MAC_OS);
            setStaticField(environmentClass, "ARCH", SIMULATED_MAC_ARCH);

            String fallbackLibraryFileName = (String) environmentClass
                    .getMethod("getFallbackJniLibraryFileName", String.class)
                    .invoke(null, ROCKSDB_LIBRARY_NAME);

            Class<?> loaderClass = Class.forName("org.rocksdb.NativeLibraryLoader", true, classLoader);
            Object loader = loaderClass.getMethod("getInstance").invoke(null);

            assertThat(fallbackLibraryFileName).isEqualTo("librocksdbjni-osx.jnilib");
            assertThatThrownBy(() -> invokeLoadLibraryFromJarToTemp(loader, tempDir.toString()))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage(fallbackLibraryFileName + " was not found inside JAR.");
        }
    }

    private static File invokeLoadLibraryFromJarToTemp(Object loader, String tmpDir) throws Throwable {
        Method method = loader.getClass().getDeclaredMethod("loadLibraryFromJarToTemp", String.class);
        method.setAccessible(true);
        try {
            return (File) method.invoke(loader, tmpDir);
        } catch (InvocationTargetException invocationTargetException) {
            throw invocationTargetException.getTargetException();
        }
    }

    private static void setStaticField(Class<?> type, String name, Object value) throws ReflectiveOperationException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }

    private static boolean isNativeImageRuntime() {
        return "runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"));
    }

    private static final class ResourceHidingClassLoader extends URLClassLoader {
        private final String hiddenResourceName;

        private ResourceHidingClassLoader(URL jarUrl, String hiddenResourceName) {
            super(new URL[]{jarUrl}, ClassLoader.getPlatformClassLoader());
            this.hiddenResourceName = hiddenResourceName;
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            if (hiddenResourceName.equals(name)) {
                return null;
            }
            return super.getResourceAsStream(name);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> loadedClass = findLoadedClass(name);
                if (loadedClass == null) {
                    if (name.startsWith(ROCKSDB_PACKAGE)) {
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
