/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_expressly.expressly;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import org.glassfish.expressly.ExpressionFactoryImpl;
import org.glassfish.expressly.util.ReflectionUtil;
import org.junit.jupiter.api.Test;

import jakarta.el.ELContext;
import jakarta.el.StandardELContext;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectionUtilTest {

    @Test
    void resolvesRegularAndArrayClassNames() throws ClassNotFoundException {
        assertThat(ReflectionUtil.forName("java.lang.String")).isEqualTo(String.class);
        assertThat(ReflectionUtil.forName("java.lang.String[]")).isEqualTo(String[].class);
    }

    @Test
    void findsPublicInterfaceMethodOnPackagePrivateImplementation() {
        Method method = ReflectionUtil.findMethod(
                HiddenInterfaceGreeting.class,
                "greet",
                new Class<?>[] {String.class},
                new Object[] {"Duke"});

        assertThat(method).isNotNull();
        assertThat(method.getDeclaringClass()).isEqualTo(Greeting.class);
    }

    @Test
    void findsPublicSuperclassMethodOnPackagePrivateSubclass() {
        Method method = ReflectionUtil.findMethod(
                HiddenBaseGreeting.class,
                "inheritedGreeting",
                new Class<?>[] {String.class},
                new Object[] {"Duke"});

        assertThat(method).isNotNull();
        assertThat(method.getDeclaringClass()).isEqualTo(BaseGreeting.class);
    }

    @Test
    void invokesVarArgsMethodWithConvertedParameters() {
        ELContext context = new StandardELContext(new ExpressionFactoryImpl());
        VarArgsTarget target = new VarArgsTarget();
        Object[] arguments = new Object[] {"hello", "native", "image"};
        Method method = ReflectionUtil.findMethod(
                VarArgsTarget.class,
                "join",
                new Class<?>[] {String.class, String.class, String.class},
                arguments);

        Object result = ReflectionUtil.invokeMethod(context, method, target, arguments);

        assertThat(result).isEqualTo("hello:native:image");
    }

    @Test
    void findsPublicSuperclassConstructorForPackagePrivateSubclass() throws Throwable {
        Constructor<?> hiddenConstructor = HiddenConstructed.class.getDeclaredConstructor(String.class);
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(ReflectionUtil.class, MethodHandles.lookup());
        MethodHandle getConstructor = lookup.findStatic(
                ReflectionUtil.class,
                "getConstructor",
                MethodType.methodType(Constructor.class, Class.class, Constructor.class));

        Constructor<?> constructor = (Constructor<?>) getConstructor.invoke(HiddenConstructed.class, hiddenConstructor);

        assertThat(constructor).isNotNull();
        assertThat(constructor.getDeclaringClass()).isEqualTo(BaseConstructed.class);
    }

    public interface Greeting {
        String greet(String name);
    }

    static class HiddenInterfaceGreeting implements Greeting {
        @Override
        public String greet(String name) {
            return "Hello " + name;
        }
    }

    public static class BaseGreeting {
        public String inheritedGreeting(String name) {
            return "Hello " + name;
        }
    }

    static class HiddenBaseGreeting extends BaseGreeting {
    }

    public static class VarArgsTarget {
        public String join(String prefix, String... values) {
            return prefix + ":" + String.join(":", values);
        }
    }

    public static class BaseConstructed {
        public BaseConstructed(String value) {
        }
    }

    static class HiddenConstructed extends BaseConstructed {
        HiddenConstructed(String value) {
            super(value);
        }
    }
}
