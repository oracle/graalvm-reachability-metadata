/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_activation.activation;

import java.io.File;
import java.security.Permission;

import javax.activation.FileTypeMap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

import static org.assertj.core.api.Assertions.assertThat;

public class FileTypeMapTest {
    @Test
    @DisabledIfSystemProperty(named = "org.graalvm.nativeimage.imagecode", matches = "runtime")
    void setDefaultFileTypeMapHandlesDeniedSetFactoryFromSameClassLoader() {
        FileTypeMap originalFileTypeMap = FileTypeMap.getDefaultFileTypeMap();
        SecurityManager originalSecurityManager = System.getSecurityManager();
        FileTypeMap replacementFileTypeMap = new TestFileTypeMap();

        try {
            System.setSecurityManager(new DenyingSetFactorySecurityManager());

            FileTypeMap.setDefaultFileTypeMap(replacementFileTypeMap);

            assertThat(FileTypeMap.getDefaultFileTypeMap()).isSameAs(replacementFileTypeMap);
        } finally {
            System.setSecurityManager(originalSecurityManager);
            FileTypeMap.setDefaultFileTypeMap(originalFileTypeMap);
        }
    }

    private static final class DenyingSetFactorySecurityManager extends SecurityManager {
        @Override
        public void checkPermission(Permission permission) {
        }

        @Override
        public void checkPermission(Permission permission, Object context) {
        }

        @Override
        public void checkSetFactory() {
            throw new SecurityException("setFactory denied for test coverage");
        }
    }

    private static final class TestFileTypeMap extends FileTypeMap {
        @Override
        public String getContentType(File file) {
            return getContentType(file.getName());
        }

        @Override
        public String getContentType(String filename) {
            if (filename.endsWith(".txt")) {
                return "text/plain";
            }
            return "application/octet-stream";
        }
    }
}
