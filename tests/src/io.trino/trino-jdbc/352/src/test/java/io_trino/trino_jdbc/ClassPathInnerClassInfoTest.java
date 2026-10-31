/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino.trino_jdbc;

import io.trino.jdbc.$internal.guava.reflect.ClassPath;
import io.trino.jdbc.$internal.guava.reflect.ClassPath.ClassInfo;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ClassPathInnerClassInfoTest {
    @Test
    @ResourceLock(Resources.SYSTEM_PROPERTIES)
    void discoversAndLoadsAnExistingPlatformClass(@TempDir Path classPathRoot) throws Exception {
        Path classFile = classPathRoot.resolve("java").resolve("lang").resolve("String.class");
        Files.createDirectories(classFile.getParent());
        try (InputStream bytecode = String.class.getResourceAsStream("String.class")) {
            assertNotNull(bytecode);
            Files.copy(bytecode, classFile);
        }

        String previousClassPath = System.getProperty("java.class.path");
        try {
            System.setProperty("java.class.path", classPathRoot.toString());
            ClassInfo info = ClassPath.from(ClassLoader.getSystemClassLoader())
                    .getTopLevelClasses("java.lang").stream()
                    .filter(candidate -> candidate.getName().equals("java.lang.String"))
                    .findFirst().orElseThrow();

            assertEquals("java.lang", info.getPackageName());
            assertEquals("String", info.getSimpleName());
            assertEquals("java/lang/String.class", info.getResourceName());
            assertTrue(info.isTopLevel());
            assertSame(String.class, info.load());
        } finally {
            if (previousClassPath == null) {
                System.clearProperty("java.class.path");
            } else {
                System.setProperty("java.class.path", previousClassPath);
            }
        }
    }
}
