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

import java.io.IOException;
import java.io.InputStream;
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
    void loadsDirectByteBufferCleanerFallbackWhenUnsafeIsUnavailable() throws Exception {
        ClassLoader loader = new CleanerUtilIsolationClassLoader(CleanerUtilDynamicAccessTest.class.getClassLoader());

        Class<?> isolatedCleanerUtilClass = Class.forName(CleanerUtil.class.getName(), true, loader);
        boolean unmapSupported = isolatedCleanerUtilClass.getField("UNMAP_SUPPORTED").getBoolean(null);
        Object cleaner = isolatedCleanerUtilClass.getMethod("getCleaner").invoke(null);
        Object reason = isolatedCleanerUtilClass.getField("UNMAP_NOT_SUPPORTED_REASON").get(null);

        assertThat(unmapSupported).isTrue();
        assertThat(cleaner).isNotNull();
        assertThat(reason).isNull();
    }

    private static final class CleanerUtilIsolationClassLoader extends ClassLoader {

        private static final Set<String> ISOLATED_CLASSES = Set.of(
                CleanerUtil.class.getName(),
                CleanerUtil.class.getName() + "$BufferCleaner"
        );

        private CleanerUtilIsolationClassLoader(ClassLoader parent) {
            super(parent);
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
                    Class<?> definedClass = defineIsolatedClass(name);
                    if (resolve) {
                        resolveClass(definedClass);
                    }
                    return definedClass;
                }
                return super.loadClass(name, resolve);
            }
        }

        private Class<?> defineIsolatedClass(String name) throws ClassNotFoundException {
            String resourceName = name.replace('.', '/') + ".class";
            try (InputStream inputStream = getParent().getResourceAsStream(resourceName)) {
                if (inputStream == null) {
                    throw new ClassNotFoundException(name);
                }
                byte[] classBytes = inputStream.readAllBytes();
                return defineClass(name, classBytes, 0, classBytes.length);
            } catch (IOException exception) {
                throw new ClassNotFoundException(name, exception);
            }
        }
    }
}
