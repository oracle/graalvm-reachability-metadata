/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_guava.guava;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.reflect.ClassPath;
import org.junit.jupiter.api.Test;

public class ClassPathInnerClassInfoTest {
    private static final String CLASS_PATH_CLASS_NAME = "com.google.common.reflect.ClassPath";
    private static final String CLASS_PATH_PACKAGE_NAME = "com.google.common.reflect";

    @Test
    void loadReturnsClassDiscoveredOnApplicationClassPath() throws Exception {
        ClassLoader loader = ClassPath.class.getClassLoader();
        ClassPath.ClassInfo classInfo = ClassPath.from(loader).getTopLevelClasses(CLASS_PATH_PACKAGE_NAME).stream()
                .filter(candidate -> candidate.getName().equals(CLASS_PATH_CLASS_NAME))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Could not find " + CLASS_PATH_CLASS_NAME));

        assertThat(classInfo.getPackageName()).isEqualTo(CLASS_PATH_PACKAGE_NAME);
        assertThat(classInfo.getSimpleName()).isEqualTo("ClassPath");
        assertThat(classInfo.isTopLevel()).isTrue();
        assertThat(classInfo.load()).isSameAs(ClassPath.class);
    }
}
