/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish.jakarta_el;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import jakarta.el.ELContext;
import jakarta.el.StandardELContext;

import org.junit.jupiter.api.Test;

import com.sun.el.ExpressionFactoryImpl;
import com.sun.el.util.ReflectionUtil;

public class ComSunElUtilReflectionUtilTest {
    @Test
    void forNameLoadsRegularClassesAndSourceStyleArrayNames() throws Exception {
        Class<?> stringType = ReflectionUtil.forName("java.lang.String");
        Class<?> stringArrayType = ReflectionUtil.forName("java.lang.String[]");

        assertThat(stringType).isSameAs(String.class);
        assertThat(stringArrayType).isSameAs(String[].class);
    }

    @Test
    void findMethodExposesPublicInterfaceMethodForNonPublicImplementingClass() {
        PrivateInterfaceGreeter greeter = new PrivateInterfaceGreeter();

        Method method = ReflectionUtil.findMethod(PrivateInterfaceGreeter.class, "greet",
                new Class<?>[] { String.class }, new Object[] { "Duke" });
        Object result = ReflectionUtil.invokeMethod(elContext(), method, greeter,
                new Object[] { "Duke" });

        assertThat(method.getDeclaringClass()).isSameAs(GreetingOperations.class);
        assertThat(result).isEqualTo("Hello Duke");
    }

    @Test
    void findMethodExposesPublicSuperclassMethodForNonPublicSubclass() {
        PrivateSubclass greeter = new PrivateSubclass();

        Method method = ReflectionUtil.findMethod(PrivateSubclass.class, "inheritedGreeting", null,
                new Object[] { "Ada" });
        Object result = ReflectionUtil.invokeMethod(elContext(), method, greeter,
                new Object[] { "Ada" });

        assertThat(method.getDeclaringClass()).isSameAs(PublicGreetingBase.class);
        assertThat(result).isEqualTo("Welcome Ada");
    }

    @Test
    void invokeMethodBuildsVarArgsArray() {
        PublicVarargsBean bean = new PublicVarargsBean();

        Method method = ReflectionUtil.findMethod(PublicVarargsBean.class, "join",
                new Class<?>[] { String.class, String.class, String.class },
                new Object[] { "Names", "Ada", "Duke" });
        Object result = ReflectionUtil.invokeMethod(elContext(), method, bean,
                new Object[] { "Names", "Ada", "Duke" });

        assertThat(result).isEqualTo("Names: Ada, Duke");
    }

    private static ELContext elContext() {
        return new StandardELContext(new ExpressionFactoryImpl());
    }

    public interface GreetingOperations {
        String greet(String name);
    }

    private static final class PrivateInterfaceGreeter implements GreetingOperations {
        @Override
        public String greet(String name) {
            return "Hello " + name;
        }
    }

    public static class PublicGreetingBase {
        public String inheritedGreeting(String name) {
            return "Welcome " + name;
        }
    }

    private static final class PrivateSubclass extends PublicGreetingBase {
    }

    public static class PublicVarargsBean {
        public String join(String label, String... names) {
            return label + ": " + String.join(", ", names);
        }
    }
}
