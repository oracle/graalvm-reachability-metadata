/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_xerial_snappy.snappy_java;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ServiceLoader;
import java.util.concurrent.Callable;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xerial.snappy.Snappy;
import org.xerial.snappy.SnappyError;
import org.xerial.snappy.SnappyLoader;

public class SnappyLoaderTest {
    private static final String CHILD_TEMPDIR_PROPERTY = "snappy.loader.child.tempdir";

    @TempDir
    Path tempDir;

    @Test
    void loadsSystemLibraryAndBundledNativeLibraryFallback() throws Exception {
        clearSnappyProperties();

        try {
            loadBundledNativeLibraryInIsolatedClassLoader();
            System.setProperty(SnappyLoader.KEY_SNAPPY_USE_SYSTEMLIB, "true");
            loadSystemLibraryThroughPublicApi();
        } catch (Error error) {
            rethrowUnlessUnsupportedFeatureError(error);
        } finally {
            clearSnappyProperties();
            System.clearProperty(CHILD_TEMPDIR_PROPERTY);
        }
    }

    private void loadSystemLibraryThroughPublicApi() throws Exception {
        try {
            byte[] input = "SnappyLoader loads a preinstalled JNI library".getBytes(StandardCharsets.UTF_8);
            byte[] compressed = Snappy.compress(input);
            assertThat(Snappy.uncompress(compressed)).isEqualTo(input);
            assertThat(SnappyLoader.isNativeLibraryLoaded()).isTrue();
        } catch (SnappyError error) {
            assertThat(error.getMessage()).contains("FAILED_TO_LOAD_NATIVE_LIBRARY");
        }
    }

    private void loadBundledNativeLibraryInIsolatedClassLoader() throws Exception {
        Path providerConfiguration = tempDir.resolve("META-INF/services/java.util.concurrent.Callable");
        Files.createDirectories(providerConfiguration.getParent());
        Files.writeString(providerConfiguration, BundledLibraryCallable.class.getName(), StandardCharsets.UTF_8);

        System.setProperty(CHILD_TEMPDIR_PROPERTY, tempDir.resolve("child-loader").toString());
        try (URLClassLoader classLoader = new URLClassLoader(currentClasspathUrlsWithProviderRoot(), null)) {
            Callable<?> bundledLibraryLoader = ServiceLoader.load(Callable.class, classLoader)
                    .findFirst()
                    .orElseThrow();
            assertThat(bundledLibraryLoader.call()).isEqualTo(Boolean.TRUE);
        }
    }

    private URL[] currentClasspathUrlsWithProviderRoot() throws Exception {
        String[] classpathEntries = System.getProperty("java.class.path").split(File.pathSeparator);
        URL[] urls = new URL[classpathEntries.length + 1];
        urls[0] = tempDir.toUri().toURL();
        for (int i = 0; i < classpathEntries.length; i++) {
            urls[i + 1] = Path.of(classpathEntries[i]).toUri().toURL();
        }
        return urls;
    }

    private static void clearSnappyProperties() {
        System.clearProperty(SnappyLoader.KEY_SNAPPY_TEMPDIR);
        System.clearProperty(SnappyLoader.KEY_SNAPPY_USE_SYSTEMLIB);
        System.clearProperty(SnappyLoader.KEY_SNAPPY_DISABLE_BUNDLED_LIBS);
        System.clearProperty(SnappyLoader.KEY_SNAPPY_LIB_PATH);
        System.clearProperty(SnappyLoader.KEY_SNAPPY_LIB_NAME);
    }

    private static void rethrowUnlessUnsupportedFeatureError(Error error) {
        if (NativeImageSupport.isUnsupportedFeatureError(error)) {
            return;
        }
        if (error.getCause() instanceof Error cause && NativeImageSupport.isUnsupportedFeatureError(cause)) {
            return;
        }
        throw error;
    }

    public static class BundledLibraryCallable implements Callable<Boolean> {
        @Override
        public Boolean call() throws Exception {
            ClassLoader previousClassLoader = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader(BundledLibraryCallable.class.getClassLoader());
            try {
                clearSnappyProperties();
                Path nativeLibraryDirectory = Path.of(System.getProperty(CHILD_TEMPDIR_PROPERTY));
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
}
