/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_poi.poi;

import org.apache.poi.poifs.nio.CleanerUtil;
import org.apache.poi.poifs.nio.FileBackedDataSource;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class CleanerUtilDynamicAccessTest {

    @Test
    void mapsAndReleasesFileBackedBuffers() throws Exception {
        Path file = Files.createTempFile("poi-cleaner", ".bin");
        Files.write(file, new byte[]{1, 2, 3, 4});

        try (FileBackedDataSource dataSource = new FileBackedDataSource(file.toFile(), false)) {
            ByteBuffer buffer = dataSource.read(4, 0);

            assertThat(buffer.isDirect()).isTrue();
            assertThat(buffer.get(0)).isEqualTo((byte) 1);

            dataSource.releaseBuffer(buffer);

            if (CleanerUtil.UNMAP_SUPPORTED) {
                assertThat(CleanerUtil.getCleaner()).isNotNull();
            } else {
                assertThat(CleanerUtil.UNMAP_NOT_SUPPORTED_REASON).isNotBlank();
            }
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void initializesCleanerUtilWithTheDirectByteBufferCleanerFallback() throws Exception {
        URL poiJarUrl = CleanerUtil.class.getProtectionDomain().getCodeSource().getLocation();

        try (CleanerUtilIsolationClassLoader loader = new CleanerUtilIsolationClassLoader(
                poiJarUrl,
                CleanerUtilDynamicAccessTest.class.getClassLoader()
        )) {
            Class<?> isolatedCleanerUtilClass = Class.forName(CleanerUtil.class.getName(), true, loader);

            assertThat(isolatedCleanerUtilClass.getField("UNMAP_SUPPORTED").getBoolean(null)).isTrue();
            assertThat(isolatedCleanerUtilClass.getMethod("getCleaner").invoke(null)).isNotNull();
            assertThat(isolatedCleanerUtilClass.getField("UNMAP_NOT_SUPPORTED_REASON").get(null)).isNull();
        }
    }

    private static final class CleanerUtilIsolationClassLoader extends URLClassLoader {

        private static final Set<String> ISOLATED_CLASSES = Set.of(
                CleanerUtil.class.getName(),
                CleanerUtil.class.getName() + "$BufferCleaner"
        );

        private CleanerUtilIsolationClassLoader(URL poiJarUrl, ClassLoader parent) {
            super(new URL[]{poiJarUrl}, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> loadedClass = findLoadedClass(name);
                if (loadedClass != null) {
                    return loadedClass;
                }
                if ("sun.misc.Unsafe".equals(name)) {
                    throw new ClassNotFoundException(name);
                }
                if (ISOLATED_CLASSES.contains(name)) {
                    Class<?> isolatedClass = findClass(name);
                    if (resolve) {
                        resolveClass(isolatedClass);
                    }
                    return isolatedClass;
                }
                return super.loadClass(name, resolve);
            }
        }
    }
}
