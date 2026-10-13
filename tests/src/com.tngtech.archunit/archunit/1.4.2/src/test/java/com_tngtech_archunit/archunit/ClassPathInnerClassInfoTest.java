/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_tngtech_archunit.archunit;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.thirdparty.com.google.common.reflect.ClassPath;
import com.tngtech.archunit.thirdparty.com.google.common.reflect.ClassPath.ClassInfo;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

public class ClassPathInnerClassInfoTest {
    private static final String TEST_PACKAGE = "com_tngtech_archunit.archunit";
    private static final Path TEST_CLASSES = Path.of("build", "classes", "java", "test");

    @Test
    void discoversAndLoadsATopLevelClassFromTheApplicationClassPath() throws Exception {
        String originalClassPath = System.getProperty("java.class.path");
        System.setProperty("java.class.path", TEST_CLASSES.toAbsolutePath().toString());
        try {
            ClassInfo classInfo = ClassPath.from(ClassLoader.getSystemClassLoader())
                    .getTopLevelClasses(TEST_PACKAGE).stream()
                    .filter(info -> info.getName().equals(ClassPathInnerClassInfoTest.class.getName()))
                    .findFirst()
                    .orElseThrow();

            assertThat(classInfo.getPackageName()).isEqualTo(TEST_PACKAGE);
            assertThat(classInfo.getSimpleName()).isEqualTo("ClassPathInnerClassInfoTest");
            assertThat(classInfo.isTopLevel()).isTrue();
            assertThat(classInfo.load()).isSameAs(ClassPathInnerClassInfoTest.class);
        } finally {
            if (originalClassPath == null) {
                System.clearProperty("java.class.path");
            } else {
                System.setProperty("java.class.path", originalClassPath);
            }
        }
    }
}
