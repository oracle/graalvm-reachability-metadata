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

import java.io.File;
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
    void releasesMappedBuffersWithTheDirectByteBufferCleanerFallback() throws Exception {
        Path file = Files.createTempFile("poi-cleaner-fallback", ".bin");
        Files.write(file, new byte[]{5, 6, 7, 8});

        try {
            ClassLoader loader = new CleanerUtilIsolationClassLoader(CleanerUtilDynamicAccessTest.class.getClassLoader());
            Class<?> isolatedCleanerUtilClass = loader.loadClass(CleanerUtil.class.getName());
            Class<?> isolatedDataSourceClass = loader.loadClass(FileBackedDataSource.class.getName());

            boolean unmapSupported = isolatedCleanerUtilClass.getField("UNMAP_SUPPORTED").getBoolean(null);
            Object cleaner = isolatedCleanerUtilClass.getMethod("getCleaner").invoke(null);
            Object reason = isolatedCleanerUtilClass.getField("UNMAP_NOT_SUPPORTED_REASON").get(null);

            assertThat(unmapSupported).isTrue();
            assertThat(cleaner).isNotNull();
            assertThat(reason).isNull();

            Object dataSource = isolatedDataSourceClass
                    .getConstructor(File.class, boolean.class)
                    .newInstance(file.toFile(), false);
            try {
                ByteBuffer buffer = (ByteBuffer) isolatedDataSourceClass
                        .getMethod("read", int.class, long.class)
                        .invoke(dataSource, 4, 0L);

                assertThat(buffer.isDirect()).isTrue();
                assertThat(buffer.get(0)).isEqualTo((byte) 5);

                isolatedDataSourceClass.getMethod("releaseBuffer", ByteBuffer.class).invoke(dataSource, buffer);
            } finally {
                isolatedDataSourceClass.getMethod("close").invoke(dataSource);
            }
        } finally {
            Files.deleteIfExists(file);
        }
    }

    private static final class CleanerUtilIsolationClassLoader extends ClassLoader {

        private static final Set<String> ISOLATED_CLASSES = Set.of(
                CleanerUtil.class.getName(),
                CleanerUtil.class.getName() + "$BufferCleaner",
                FileBackedDataSource.class.getName()
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
            String resourceName = getIsolatedClassResourceName(name);
            try (InputStream inputStream = CleanerUtilDynamicAccessTest.class.getResourceAsStream(resourceName)) {
                if (inputStream == null) {
                    throw new ClassNotFoundException(name);
                }
                byte[] classBytes = inputStream.readAllBytes();
                return defineClass(name, classBytes, 0, classBytes.length);
            } catch (IOException exception) {
                throw new ClassNotFoundException(name, exception);
            }
        }

        private String getIsolatedClassResourceName(String name) throws ClassNotFoundException {
            if (CleanerUtil.class.getName().equals(name)) {
                return "/org/apache/poi/poifs/nio/CleanerUtil.class";
            }
            if ((CleanerUtil.class.getName() + "$BufferCleaner").equals(name)) {
                return "/org/apache/poi/poifs/nio/CleanerUtil$BufferCleaner.class";
            }
            if (FileBackedDataSource.class.getName().equals(name)) {
                return "/org/apache/poi/poifs/nio/FileBackedDataSource.class";
            }
            throw new ClassNotFoundException(name);
        }
    }
}
