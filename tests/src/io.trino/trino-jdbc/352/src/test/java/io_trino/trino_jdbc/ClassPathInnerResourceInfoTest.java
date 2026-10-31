/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino.trino_jdbc;

import io.trino.jdbc.$internal.guava.reflect.ClassPath;
import io.trino.jdbc.$internal.guava.reflect.ClassPath.ResourceInfo;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ClassPathInnerResourceInfoTest {
    @Test
    @ResourceLock(Resources.SYSTEM_PROPERTIES)
    void discoversAndReadsAnExistingPlatformResource(@TempDir Path classPathRoot) throws Exception {
        byte[] expected;
        try (InputStream bytecode = String.class.getResourceAsStream("String.class")) {
            assertNotNull(bytecode);
            expected = bytecode.readAllBytes();
        }
        Path classFile = classPathRoot.resolve("java").resolve("lang").resolve("String.class");
        Files.createDirectories(classFile.getParent());
        Files.write(classFile, expected);

        String previousClassPath = System.getProperty("java.class.path");
        try {
            System.setProperty("java.class.path", classPathRoot.toString());
            ResourceInfo resource = ClassPath.from(ClassLoader.getSystemClassLoader())
                    .getResources().stream()
                    .filter(candidate -> candidate.getResourceName().equals("java/lang/String.class"))
                    .findFirst().orElseThrow();

            assertEquals("java/lang/String.class", resource.getResourceName());
            try (InputStream contents = resource.url().openStream()) {
                assertArrayEquals(expected, contents.readAllBytes());
            }
            assertArrayEquals(expected, resource.asByteSource().read());
        } finally {
            if (previousClassPath == null) {
                System.clearProperty("java.class.path");
            } else {
                System.setProperty("java.class.path", previousClassPath);
            }
        }
    }
}
