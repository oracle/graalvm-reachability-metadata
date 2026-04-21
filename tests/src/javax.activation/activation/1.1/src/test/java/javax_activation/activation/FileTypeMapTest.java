/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_activation.activation;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.Permission;

import javax.activation.FileTypeMap;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FileTypeMapTest {
    private static final String TEST_CONTENT_TYPE = "application/x-file-type-map-test";

    @Test
    @SuppressWarnings("removal")
    void setDefaultFileTypeMapAllowsSameClassLoaderWhenSetFactoryIsDenied() throws Throwable {
        final FileTypeMap previousDefaultFileTypeMap = FileTypeMap.getDefaultFileTypeMap();
        final SecurityManager previousSecurityManager = System.getSecurityManager();
        final TestFileTypeMap replacementFileTypeMap = new TestFileTypeMap();
        final DenySetFactorySecurityManager securityManager = new DenySetFactorySecurityManager();
        final boolean securityManagerInstalled = installSecurityManagerIfSupported(securityManager);

        try {
            FileTypeMap.setDefaultFileTypeMap(replacementFileTypeMap);

            assertThat(FileTypeMap.getDefaultFileTypeMap()).isSameAs(replacementFileTypeMap);
            assertThat(invokeSyntheticClassLookup()).isSameAs(FileTypeMap.class);
            if (securityManagerInstalled) {
                assertThat(securityManager.wasCheckSetFactoryCalled()).isTrue();
            }
        } finally {
            if (securityManagerInstalled) {
                System.setSecurityManager(previousSecurityManager);
            }
            FileTypeMap.setDefaultFileTypeMap(previousDefaultFileTypeMap);
        }
    }

    @SuppressWarnings("removal")
    private static boolean installSecurityManagerIfSupported(final SecurityManager securityManager) {
        try {
            System.setSecurityManager(securityManager);
            return System.getSecurityManager() == securityManager;
        } catch (final UnsupportedOperationException unsupportedOperationException) {
            return false;
        }
    }

    private static Class<?> invokeSyntheticClassLookup()
            throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        final Method classLookup = FileTypeMap.class.getDeclaredMethod("class$", String.class);
        classLookup.setAccessible(true);
        return (Class<?>) classLookup.invoke(null, "javax.activation.FileTypeMap");
    }

    private static final class TestFileTypeMap extends FileTypeMap {
        @Override
        public String getContentType(final File file) {
            return TEST_CONTENT_TYPE;
        }

        @Override
        public String getContentType(final String filename) {
            return TEST_CONTENT_TYPE;
        }
    }

    @SuppressWarnings("removal")
    private static final class DenySetFactorySecurityManager extends SecurityManager {
        private boolean checkSetFactoryCalled;

        @Override
        public void checkPermission(final Permission permission) {
        }

        @Override
        public void checkSetFactory() {
            checkSetFactoryCalled = true;
            throw new SecurityException("setFactory denied for coverage test");
        }

        private boolean wasCheckSetFactoryCalled() {
            return checkSetFactoryCalled;
        }
    }
}
