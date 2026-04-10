/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_activation.activation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.security.Permission;
import javax.activation.FileTypeMap;
import org.junit.jupiter.api.Test;

class FileTypeMapTest {
    private static final String NATIVE_IMAGE_CODE_PROPERTY = "org.graalvm.nativeimage.imagecode";

    @Test
    @SuppressWarnings("removal")
    void setDefaultFileTypeMapFallsBackToFileTypeMapClassLookupWhenCheckSetFactoryIsRejected() {
        if (System.getProperty(NATIVE_IMAGE_CODE_PROPERTY) != null) {
            return;
        }

        FileTypeMap originalFileTypeMap = FileTypeMap.getDefaultFileTypeMap();
        TestFileTypeMap testFileTypeMap = new TestFileTypeMap();

        assertThat(testFileTypeMap.getClass().getClassLoader()).isSameAs(FileTypeMap.class.getClassLoader());

        SecurityManager originalSecurityManager = System.getSecurityManager();
        System.setSecurityManager(new RejectingSetFactorySecurityManager());
        try {
            FileTypeMap.setDefaultFileTypeMap(testFileTypeMap);
        } finally {
            System.setSecurityManager(originalSecurityManager);
        }

        try {
            assertThat(FileTypeMap.getDefaultFileTypeMap()).isSameAs(testFileTypeMap);
        } finally {
            FileTypeMap.setDefaultFileTypeMap(originalFileTypeMap);
        }
    }

    private static final class TestFileTypeMap extends FileTypeMap {
        @Override
        public String getContentType(File file) {
            return "application/octet-stream";
        }

        @Override
        public String getContentType(String filename) {
            return "application/octet-stream";
        }
    }

    private static final class RejectingSetFactorySecurityManager extends SecurityManager {
        @Override
        public void checkPermission(Permission permission) {
        }

        @Override
        public void checkSetFactory() {
            throw new SecurityException("rejected by test security manager");
        }
    }
}
