/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_xerial_snappy.snappy_java;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.ServiceLoader;
import java.util.concurrent.Callable;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xerial.snappy.Snappy;
import org.xerial.snappy.SnappyLoader;

public class SnappyLoaderTest {
    private static final String CHILD_TEMPDIR_PROPERTY = "snappy.loader.child.tempdir";
    private static final String ISOLATED_CALLABLE_CLASS_PREFIX = SnappyLoaderTest.class.getName();
    private static final String SNAPPY_PACKAGE = "org.xerial.snappy.";

    @TempDir
    Path tempDir;

    @Test
    void loadsSystemLibraryFallbackAndBundledNativeLibrary() throws Exception {
        clearSnappyProperties();

        try {
            System.setProperty(CHILD_TEMPDIR_PROPERTY, tempDir.resolve("child-loader").toString());
            assertThat(runCallableProvider(SystemLibraryCallable.class.getName())).isTrue();
            assertThat(runCallableProvider(BundledLibraryCallable.class.getName())).isTrue();
        } catch (Throwable throwable) {
            rethrowUnlessUnsupportedFeatureError(throwable);
        } finally {
            clearSnappyProperties();
            System.clearProperty(CHILD_TEMPDIR_PROPERTY);
        }
    }

    private boolean runCallableProvider(String providerClassName) throws Exception {
        Path providerConfiguration = tempDir.resolve("META-INF/services/java.util.concurrent.Callable");
        Files.createDirectories(providerConfiguration.getParent());
        Files.writeString(providerConfiguration, providerClassName + System.lineSeparator(), StandardCharsets.UTF_8);

        try (URLClassLoader classLoader = createProviderClassLoader()) {
            Callable<?> loader = ServiceLoader.load(Callable.class, classLoader).findFirst().orElseThrow();
            return (Boolean) loader.call();
        }
    }

    private URLClassLoader createProviderClassLoader() throws Exception {
        if ("runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"))) {
            return new ChildFirstClassLoader(new URL[]{tempDir.toUri().toURL()}, SnappyLoaderTest.class.getClassLoader());
        }
        return new URLClassLoader(currentClasspathUrlsWithProviderRoot(), null);
    }

    private URL[] currentClasspathUrlsWithProviderRoot() throws Exception {
        Set<URL> urls = new LinkedHashSet<>();
        urls.add(tempDir.toUri().toURL());
        String classPath = System.getProperty("java.class.path");
        if (classPath != null && !classPath.isEmpty()) {
            for (String entry : classPath.split(File.pathSeparator)) {
                if (!entry.isEmpty()) {
                    urls.add(Path.of(entry).toUri().toURL());
                }
            }
        }
        return urls.toArray(URL[]::new);
    }

    private static void clearSnappyProperties() {
        System.clearProperty(SnappyLoader.KEY_SNAPPY_TEMPDIR);
        System.clearProperty(SnappyLoader.KEY_SNAPPY_USE_SYSTEMLIB);
        System.clearProperty(SnappyLoader.KEY_SNAPPY_DISABLE_BUNDLED_LIBS);
        System.clearProperty(SnappyLoader.KEY_SNAPPY_LIB_PATH);
        System.clearProperty(SnappyLoader.KEY_SNAPPY_LIB_NAME);
    }

    private static void rethrowUnlessUnsupportedFeatureError(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current instanceof Error error && NativeImageSupport.isUnsupportedFeatureError(error)) {
                return;
            }
        }
        if (hasUnsupportedSnappyNativeLoaderFailure(throwable)) {
            return;
        }
        if (throwable instanceof Exception exception) {
            throw new RuntimeException(exception);
        }
        throw (Error) throwable;
    }

    private static boolean hasUnsupportedSnappyNativeLoaderFailure(Throwable throwable) {
        if (!"runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"))) {
            return false;
        }

        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (!current.getClass().getName().equals("org.xerial.snappy.SnappyError")) {
                continue;
            }
            for (StackTraceElement stackTraceElement : current.getStackTrace()) {
                if (stackTraceElement.getClassName().equals("org.xerial.snappy.SnappyLoader")
                        && stackTraceElement.getMethodName().equals("injectSnappyNativeLoader")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static final class ChildFirstClassLoader extends URLClassLoader {
        private ChildFirstClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> loadedClass = findLoadedClass(name);
                if (loadedClass == null) {
                    loadedClass = loadUnresolvedClass(name);
                }
                if (resolve) {
                    resolveClass(loadedClass);
                }
                return loadedClass;
            }
        }

        private Class<?> loadUnresolvedClass(String name) throws ClassNotFoundException {
            if (!isChildFirst(name)) {
                return super.loadClass(name, false);
            }

            try {
                return findClass(name);
            } catch (ClassNotFoundException ignored) {
                return super.loadClass(name, false);
            }
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            String resourceName = name.replace('.', '/') + ".class";

            try (InputStream inputStream = getParent().getResourceAsStream(resourceName)) {
                if (inputStream == null) {
                    return super.findClass(name);
                }
                byte[] classBytes = inputStream.readAllBytes();
                return defineClass(name, classBytes, 0, classBytes.length);
            } catch (IOException exception) {
                throw new ClassNotFoundException(name, exception);
            }
        }

        private boolean isChildFirst(String className) {
            return className.startsWith(SNAPPY_PACKAGE) || className.startsWith(ISOLATED_CALLABLE_CLASS_PREFIX);
        }
    }

    public static class BundledLibraryCallable implements Callable<Boolean> {
        @Override
        public Boolean call() throws Exception {
            ClassLoader previousClassLoader = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader(BundledLibraryCallable.class.getClassLoader());
            try {
                clearSnappyProperties();
                Path nativeLibraryDirectory = Path.of(System.getProperty(CHILD_TEMPDIR_PROPERTY), "bundled");
                Files.createDirectories(nativeLibraryDirectory);

                String libraryFileName = System.mapLibraryName("snappyjava");
                Path staleExtractedLibrary = nativeLibraryDirectory.resolve(
                        "snappy-" + SnappyLoader.getVersion() + "-" + libraryFileName);
                Files.writeString(staleExtractedLibrary, "stale native library", StandardCharsets.UTF_8);

                System.setProperty(SnappyLoader.KEY_SNAPPY_TEMPDIR, nativeLibraryDirectory.toString());
                byte[] input = "SnappyLoader extracts the bundled JNI library".getBytes(StandardCharsets.UTF_8);
                byte[] compressed = Snappy.compress(input);
                assertThat(Snappy.uncompress(compressed)).isEqualTo(input);
                assertThat(Files.size(staleExtractedLibrary)).isGreaterThan((long) "stale native library".length());
                return SnappyLoader.isNativeLibraryLoaded();
            } finally {
                clearSnappyProperties();
                Thread.currentThread().setContextClassLoader(previousClassLoader);
            }
        }
    }

    public static class SystemLibraryCallable implements Callable<Boolean> {
        @Override
        public Boolean call() throws Exception {
            ClassLoader previousClassLoader = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader(SystemLibraryCallable.class.getClassLoader());
            try {
                clearSnappyProperties();
                System.setProperty(SnappyLoader.KEY_SNAPPY_USE_SYSTEMLIB, "true");
                byte[] input = "SnappyLoader invokes loadLibrary on the native loader".getBytes(StandardCharsets.UTF_8);
                byte[] compressed = Snappy.compress(input);
                assertThat(Snappy.uncompress(compressed)).isEqualTo(input);
                return SnappyLoader.isNativeLibraryLoaded();
            } finally {
                clearSnappyProperties();
                Thread.currentThread().setContextClassLoader(previousClassLoader);
            }
        }
    }
}
