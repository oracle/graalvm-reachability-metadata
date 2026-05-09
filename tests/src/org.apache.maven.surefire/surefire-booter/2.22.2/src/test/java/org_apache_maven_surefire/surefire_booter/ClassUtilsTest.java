/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_surefire.surefire_booter;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import org.apache.maven.surefire.shade.org.apache.commons.lang3.ClassUtils;
import org.junit.jupiter.api.Test;

public class ClassUtilsTest {

    @Test
    public void convertClassNamesToClassesLoadsPresentClassesAndKeepsMissingEntriesAsNull() {
        List<Class<?>> classes = ClassUtils.convertClassNamesToClasses(
                Arrays.asList(String.class.getName(), "example.missing.DoesNotExist"));

        assertThat(classes).containsExactly(String.class, null);
    }

    @Test
    public void getClassLoadsNamedClassWithProvidedClassLoaderWithoutInitialization() throws Exception {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

        Class<?> loadedClass = ClassUtils.getClass(classLoader, String.class.getName(), false);

        assertThat(loadedClass).isSameAs(String.class);
    }

    @Test
    public void getPublicMethodReturnsMethodDeclaredByPublicClassDirectly() throws Exception {
        Method method = ClassUtils.getPublicMethod(String.class, "substring", int.class, int.class);

        assertThat(method.getDeclaringClass()).isSameAs(String.class);
        assertThat(method.getReturnType()).isSameAs(String.class);
    }

    @Test
    public void getPublicMethodFindsPublicInterfaceMethodForNonPublicImplementation() throws Exception {
        Method method = ClassUtils.getPublicMethod(NonPublicImplementation.class, "visibleValue");

        assertThat(method.getDeclaringClass()).isSameAs(VisibleContract.class);
        assertThat(method.getReturnType()).isSameAs(String.class);
    }

    public interface VisibleContract {
        String visibleValue();
    }

    private static final class NonPublicImplementation implements VisibleContract {
        @Override
        public String visibleValue() {
            return "visible";
        }
    }
}
