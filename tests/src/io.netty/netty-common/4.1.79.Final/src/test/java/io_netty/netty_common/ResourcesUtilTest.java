/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_common;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;

import io.netty.util.internal.ResourcesUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ResourcesUtilTest {
    private static final String TARGET_CLASS_NAME = "io.netty.util.internal.ResourcesUtil";
    private static final String RESOURCE_NAME = "ResourcesUtilTest resource.txt";
    private static final byte[] UTF_8_CONSTANT = new byte[] {0, 5, 'U', 'T', 'F', '-', '8'};
    private static final byte[] BROKEN_ENCODING_CONSTANT = new byte[] {0, 5, 'N', 'O', 'P', 'E', '!'};

    @Test
    void getFileDecodesResourceUrlIntoAFileName() {
        File file = ResourcesUtil.getFile(ResourcesUtilTest.class, RESOURCE_NAME);

        Assertions.assertEquals(RESOURCE_NAME, file.getName());
    }

    @Test
    void getFileFallsBackToTheOriginalEncodedPathWhenDecodingFails() throws Exception {
        Class<?> isolatedResourcesUtilClass = new PatchedResourcesUtilClassLoader().loadClass(TARGET_CLASS_NAME);
        Method getFileMethod = isolatedResourcesUtilClass.getMethod("getFile", Class.class, String.class);

        File file = (File) getFileMethod.invoke(null, ResourcesUtilTest.class, RESOURCE_NAME);

        Assertions.assertEquals("ResourcesUtilTest%20resource.txt", file.getName());
    }

    private static byte[] readPatchedResourcesUtilBytes() throws IOException {
        try (InputStream inputStream = ResourcesUtilTest.class.getClassLoader().getResourceAsStream(
                "io/netty/util/internal/ResourcesUtil.class"
        )) {
            Assertions.assertNotNull(inputStream, "Expected to find ResourcesUtil.class on the classpath");

            byte[] classBytes = inputStream.readAllBytes();
            int utf8ConstantIndex = indexOf(classBytes, UTF_8_CONSTANT);
            Assertions.assertTrue(utf8ConstantIndex >= 0, "Expected to patch the UTF-8 constant in ResourcesUtil");

            System.arraycopy(BROKEN_ENCODING_CONSTANT, 0, classBytes, utf8ConstantIndex, BROKEN_ENCODING_CONSTANT.length);
            return classBytes;
        }
    }

    private static int indexOf(byte[] source, byte[] target) {
        for (int i = 0; i <= source.length - target.length; i++) {
            boolean matches = true;
            for (int j = 0; j < target.length; j++) {
                if (source[i + j] != target[j]) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return i;
            }
        }
        return -1;
    }

    private static final class PatchedResourcesUtilClassLoader extends ClassLoader {
        private PatchedResourcesUtilClassLoader() {
            super(ResourcesUtilTest.class.getClassLoader());
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> loadedClass = findLoadedClass(name);
                if (loadedClass == null && TARGET_CLASS_NAME.equals(name)) {
                    loadedClass = findClass(name);
                }
                if (loadedClass == null) {
                    loadedClass = super.loadClass(name, false);
                }
                if (resolve) {
                    resolveClass(loadedClass);
                }
                return loadedClass;
            }
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (!TARGET_CLASS_NAME.equals(name)) {
                throw new ClassNotFoundException(name);
            }
            try {
                byte[] classBytes = readPatchedResourcesUtilBytes();
                ProtectionDomain protectionDomain = ResourcesUtil.class.getProtectionDomain();
                return defineClass(name, classBytes, 0, classBytes.length, protectionDomain);
            } catch (IOException exception) {
                throw new ClassNotFoundException(name, exception);
            }
        }
    }
}
