/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_surefire.maven_surefire_common;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import org.apache.maven.surefire.shade.org.apache.commons.lang3.ClassUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassUtilsTest {
    @Test
    void convertsClassNamesToClasses() {
        List<String> classNames = Arrays.asList(
                String.class.getName(),
                ClassUtilsTest.class.getName(),
                "missing.example.Type");

        List<Class<?>> classes = ClassUtils.convertClassNamesToClasses(classNames);

        assertThat(classes).containsExactly(String.class, ClassUtilsTest.class, null);
    }

    @Test
    void loadsClassesByCanonicalNamesWithoutInitialization() throws Exception {
        Class<?> stringArrayClass = ClassUtils.getClass(
                Thread.currentThread().getContextClassLoader(), "java.lang.String[]", false);
        Class<?> entryArrayClass = ClassUtils.getClass("java.util.Map.Entry[]", false);

        assertThat(stringArrayClass).isEqualTo(String[].class);
        assertThat(entryArrayClass).isEqualTo(java.util.Map.Entry[].class);
    }

    @Test
    void findsPublicMethodsDeclaredOnPublicClasses() throws Exception {
        Method method = ClassUtils.getPublicMethod(PublicGreeting.class, "message");

        assertThat(method.getDeclaringClass()).isEqualTo(PublicGreeting.class);
        assertThat(method.getName()).isEqualTo("message");
    }

    @Test
    void findsPublicInterfaceMethodsForPackagePrivateImplementations() throws Exception {
        Method method = ClassUtils.getPublicMethod(HiddenGreeting.class, "message");

        assertThat(method.getDeclaringClass()).isEqualTo(PublicGreeting.class);
        assertThat(method.getName()).isEqualTo("message");
    }

    public interface PublicGreeting {
        String message();
    }

    private static final class HiddenGreeting implements PublicGreeting {
        @Override
        public String message() {
            return "hello";
        }
    }
}
