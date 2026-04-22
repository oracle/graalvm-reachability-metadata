/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_poi.poi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;

import org.apache.poi.poifs.nio.CleanerUtil;
import org.apache.poi.poifs.nio.FileBackedDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class CleanerUtilTest {

    private static final String CLEANER_UTIL_CLASS_NAME = "org.apache.poi.poifs.nio.CleanerUtil";
    private static final String CLEANER_UTIL_PACKAGE = "org.apache.poi.poifs.nio.";

    @Test
    void writableFileBackedDataSourceUsesDirectBuffersThatCleanerUtilCanRelease(@TempDir Path tempDir) throws Exception {
        Path path = tempDir.resolve("poi-cleaner.dat");
        Files.writeString(path, "Apache POI mapped buffer", StandardCharsets.UTF_8);

        try (FileBackedDataSource dataSource = new FileBackedDataSource(path.toFile(), false)) {
            ByteBuffer firstBuffer = dataSource.read(10, 0);
            assertThat(firstBuffer.isDirect()).isTrue();
            assertThat(StandardCharsets.UTF_8.decode(firstBuffer.duplicate()).toString()).isEqualTo("Apache POI");

            assertThat(CleanerUtil.UNMAP_SUPPORTED).isEqualTo(CleanerUtil.getCleaner() != null);
            if (CleanerUtil.UNMAP_SUPPORTED) {
                assertThat(CleanerUtil.UNMAP_NOT_SUPPORTED_REASON).isNull();
            } else {
                assertThat(CleanerUtil.UNMAP_NOT_SUPPORTED_REASON).isNotBlank();
            }

            dataSource.releaseBuffer(firstBuffer);

            ByteBuffer secondBuffer = dataSource.read(6, 11);
            assertThat(secondBuffer.isDirect()).isTrue();
            assertThat(StandardCharsets.UTF_8.decode(secondBuffer.duplicate()).toString()).isEqualTo("mapped");
        }
    }

    @Test
    void isolatedCleanerUtilFallsBackWhenUnsafeCannotBeLoaded() throws Exception {
        // Native Image resolves this path differently than the JVM on JDK 25,
        // so the URLClassLoader-based "missing Unsafe" simulation is not reliable there.
        assumeFalse(isNativeImageRuntime());

        try (UnsafeRejectingPoiClassLoader classLoader = new UnsafeRejectingPoiClassLoader(
                new URL[] {codeSourceUrl(CleanerUtil.class)},
                CleanerUtilTest.class.getClassLoader())) {
            Class<?> isolatedCleanerUtil = Class.forName(CLEANER_UTIL_CLASS_NAME, true, classLoader);

            Object cleaner = isolatedCleanerUtil.getMethod("getCleaner").invoke(null);
            boolean unmapSupported = isolatedCleanerUtil.getField("UNMAP_SUPPORTED").getBoolean(null);
            String unmapNotSupportedReason = (String) isolatedCleanerUtil.getField("UNMAP_NOT_SUPPORTED_REASON").get(null);

            assertThat(classLoader.rejectedUnsafeLoad).isTrue();
            assertThat(cleaner != null).isEqualTo(unmapSupported);
            if (unmapSupported) {
                assertThat(unmapNotSupportedReason).isNull();
            } else {
                assertThat(unmapNotSupportedReason).isNotBlank();
            }
        }
    }

    private static URL codeSourceUrl(Class<?> type) {
        CodeSource codeSource = type.getProtectionDomain().getCodeSource();
        assertThat(codeSource).isNotNull();
        return codeSource.getLocation();
    }

    private static boolean isNativeImageRuntime() {
        return "runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"));
    }

    private static final class UnsafeRejectingPoiClassLoader extends URLClassLoader {

        private boolean rejectedUnsafeLoad;

        private UnsafeRejectingPoiClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                if ("sun.misc.Unsafe".equals(name)) {
                    rejectedUnsafeLoad = true;
                    throw new ClassNotFoundException(name);
                }

                Class<?> loadedClass = findLoadedClass(name);
                if (loadedClass == null) {
                    if (name.startsWith(CLEANER_UTIL_PACKAGE)) {
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
