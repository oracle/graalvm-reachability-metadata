/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_surefire.maven_surefire_common;

import org.apache.maven.surefire.shade.org.apache.commons.lang3.ClassUtils;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassUtilsTest {
    @Test
    void convertsClassNamesToClassesAndUsesNullForUnknownNames() {
        List<Class<?>> classes = ClassUtils.convertClassNamesToClasses(
                Arrays.asList(String.class.getName(), "example.missing.DoesNotExist"));

        assertThat(classes).containsExactly(String.class, null);
    }

    @Test
    void loadsClassWithExplicitClassLoaderWithoutInitializingIt() throws ClassNotFoundException {
        ClassLoader classLoader = ClassUtilsTest.class.getClassLoader();

        Class<?> loadedClass = ClassUtils.getClass(classLoader, String.class.getName(), false);

        assertThat(loadedClass).isEqualTo(String.class);
    }

    @Test
    void resolvesPublicMethodDeclaredByPublicInterfaceOnNonPublicClass() throws NoSuchMethodException {
        Method method = ClassUtils.getPublicMethod(NonPublicContractImplementation.class, "message");

        assertThat(method.getDeclaringClass()).isEqualTo(PublicContract.class);
        assertThat(method.getName()).isEqualTo("message");
    }

    public interface PublicContract {
        String message();
    }

    private static final class NonPublicContractImplementation implements PublicContract {
        @Override
        public String message() {
            return "surefire";
        }
    }
}
