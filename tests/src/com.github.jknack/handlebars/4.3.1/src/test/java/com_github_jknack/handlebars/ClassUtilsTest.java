/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_jknack.handlebars;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.jknack.handlebars.internal.lang3.ClassUtils;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

public class ClassUtilsTest {
    @Test
    public void convertClassNamesLoadsKnownClasses() {
        List<Class<?>> classes = ClassUtils.convertClassNamesToClasses(
                Arrays.asList(String.class.getName(), Integer.class.getName()));

        assertThat(classes).containsExactly(String.class, Integer.class);
    }

    @Test
    public void getClassLoadsUsingProvidedClassLoader() throws ClassNotFoundException {
        ClassLoader classLoader = ClassUtilsTest.class.getClassLoader();
        Class<?> loadedClass = ClassUtils.getClass(classLoader, String.class.getName(), false);

        assertThat(loadedClass).isSameAs(String.class);
    }

    @Test
    public void getPublicMethodReturnsMethodDeclaredByPublicClass() throws NoSuchMethodException {
        Method method = ClassUtils.getPublicMethod(String.class, "substring", int.class);

        assertThat(method.getDeclaringClass()).isSameAs(String.class);
        assertThat(method.getName()).isEqualTo("substring");
    }

    @Test
    public void getPublicMethodFindsPublicInterfaceMethodForPrivateImplementation() throws NoSuchMethodException {
        Method method = ClassUtils.getPublicMethod(PrivateRenderer.class, "render");

        assertThat(method.getDeclaringClass()).isSameAs(PublicRenderer.class);
        assertThat(method.getName()).isEqualTo("render");
    }

    public interface PublicRenderer {
        String render();
    }

    private static final class PrivateRenderer implements PublicRenderer {
        @Override
        public String render() {
            return "rendered";
        }
    }
}
