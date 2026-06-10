/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_lang3;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.ClassUtils;
import org.junit.jupiter.api.Test;

public class ClassUtilsTest {

    @Test
    public void convertClassNamesToClassesLoadsResolvableNamesAndPreservesFailures() {
        List<Class<?>> classes = ClassUtils.convertClassNamesToClasses(Arrays.asList(
                ConvertibleTarget.class.getName(),
                "missing.Type",
                null));

        assertThat(classes).containsExactly(ConvertibleTarget.class, null, null);
    }

    @Test
    public void getClassResolvesClassNamesWithExplicitClassLoader() throws ClassNotFoundException {
        Class<?> resolvedClass = ClassUtils.getClass(
                ClassUtilsTest.class.getClassLoader(),
                LoaderTarget.class.getName(),
                false);

        assertThat(resolvedClass).isEqualTo(LoaderTarget.class);
    }

    @Test
    public void getPublicMethodFallsBackToPublicInterfaceMethod() throws NoSuchMethodException {
        Method method = ClassUtils.getPublicMethod(PackagePrivateGreetingTarget.class, "greet", String.class);

        assertThat(method).isNotNull();
        assertThat(method.getDeclaringClass()).isEqualTo(Greeting.class);
        assertThat(method.getName()).isEqualTo("greet");
        assertThat(method.getParameterTypes()).containsExactly(String.class);
    }

    public interface Greeting {
        String greet(String name);
    }

    public static class ConvertibleTarget {
    }

    public static class LoaderTarget {
    }

    static class PackagePrivateGreetingTarget implements Greeting {
        @Override
        public String greet(String name) {
            return "hello " + name;
        }
    }
}
