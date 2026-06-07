/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_xerial_snappy.snappy_java;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xerial.snappy.Snappy;
import org.xerial.snappy.SnappyLoader;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class SnappyLoaderTest {
    @Test
    void loadsBundledNativeLibraryAndRoundTripsPayload(@TempDir final Path tempDirectory) throws Exception {
        final String previousTempDirectory = System.getProperty(SnappyLoader.KEY_SNAPPY_TEMPDIR);
        System.setProperty(SnappyLoader.KEY_SNAPPY_TEMPDIR, tempDirectory.toString());

        try {
            createStaleExtractedLibrary(tempDirectory);

            final byte[] input = "Snappy loader integration payload".getBytes(StandardCharsets.UTF_8);
            final byte[] compressed = Snappy.compress(input);

            assertThat(Snappy.isValidCompressedBuffer(compressed)).isTrue();
            assertThat(Snappy.uncompress(compressed)).isEqualTo(input);
            assertThat(SnappyLoader.isNativeLibraryLoaded()).isTrue();
        } catch (final Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        } finally {
            restoreSystemProperty(SnappyLoader.KEY_SNAPPY_TEMPDIR, previousTempDirectory);
        }
    }

    @Test
    void configuredSystemLibraryLoadingUsesSystemLibraryEntryPoint() throws Exception {
        final String previousUseSystemLibrary = System.getProperty(SnappyLoader.KEY_SNAPPY_USE_SYSTEMLIB);
        final ClassLoader previousContextClassLoader = Thread.currentThread().getContextClassLoader();
        final URL snappyJar = Snappy.class.getProtectionDomain().getCodeSource().getLocation();

        System.setProperty(SnappyLoader.KEY_SNAPPY_USE_SYSTEMLIB, "true");
        try (URLClassLoader isolatedClassLoader = new URLClassLoader(new URL[] {snappyJar}, null)) {
            Thread.currentThread().setContextClassLoader(isolatedClassLoader);
            try {
                roundTripWithIsolatedSnappyApi(isolatedClassLoader);
            } catch (final InvocationTargetException exception) {
                if (!isExpectedMissingSystemLibraryFailure(exception) && !containsUnsupportedFeatureError(exception)) {
                    throw exception;
                }
            } catch (final Error error) {
                if (!isExpectedMissingSystemLibraryFailure(error)
                        && !NativeImageSupport.isUnsupportedFeatureError(error)) {
                    throw error;
                }
            }
        } finally {
            Thread.currentThread().setContextClassLoader(previousContextClassLoader);
            restoreSystemProperty(SnappyLoader.KEY_SNAPPY_USE_SYSTEMLIB, previousUseSystemLibrary);
        }
    }

    private static void roundTripWithIsolatedSnappyApi(final ClassLoader classLoader) throws Exception {
        final Class<?> snappyClass = Class.forName("org.xerial.snappy.Snappy", true, classLoader);
        final Method compress = snappyClass.getMethod("compress", byte[].class);
        final Method uncompress = snappyClass.getMethod("uncompress", byte[].class);
        final byte[] input = "system library loader payload".getBytes(StandardCharsets.UTF_8);

        final byte[] compressed = (byte[]) compress.invoke(null, (Object) input);

        assertThat((byte[]) uncompress.invoke(null, (Object) compressed)).isEqualTo(input);
    }

    private static boolean isExpectedMissingSystemLibraryFailure(final Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if ("org.xerial.snappy.SnappyError".equals(current.getClass().getName())
                    && current.getMessage().contains("FAILED_TO_LOAD_NATIVE_LIBRARY")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean containsUnsupportedFeatureError(final Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof Error && NativeImageSupport.isUnsupportedFeatureError((Error) current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static void createStaleExtractedLibrary(final Path tempDirectory) throws IOException {
        final String version = SnappyLoader.getVersion();
        final String libraryFileName = System.mapLibraryName("snappyjava");
        final Path staleLibrary = tempDirectory.resolve("snappy-" + version + "-" + libraryFileName);

        Files.write(staleLibrary, "stale native library".getBytes(StandardCharsets.UTF_8));
    }

    private static void restoreSystemProperty(final String key, final String previousValue) {
        if (previousValue == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, previousValue);
        }
    }
}
