/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_tngtech_archunit.archunit;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.thirdparty.com.google.common.reflect.ClassPath;
import org.junit.jupiter.api.Test;

public class ClassPathInnerClassInfoTest {
    @Test
    void classInfoLoadsAClassDiscoveredFromTheApplicationClassPath() throws Exception {
        ClassLoader classLoader = ClassPathInnerClassInfoTest.class.getClassLoader();
        ClassPath.ClassInfo classInfo = ClassPath.from(classLoader).getAllClasses().stream()
                .filter(info -> info.getName().equals(ClassPathInnerClassInfoTest.class.getName()))
                .findFirst()
                .orElseThrow();

        assertThat(classInfo.getSimpleName()).isEqualTo("ClassPathInnerClassInfoTest");
        assertThat(classInfo.load()).isSameAs(ClassPathInnerClassInfoTest.class);
    }
}
