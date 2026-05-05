/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cn_hutool.hutool_all;

import cn.hutool.core.lang.reflect.MethodHandleUtil;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

public class MethodHandleUtilTest {

    @Test
    void findsAndInvokesVirtualStaticSpecialAndConstructorHandles() throws Throwable {
        HandleSubject subject = new HandleSubject("initial", 3);

        MethodHandle virtualHandle = MethodHandleUtil.findMethod(
                HandleSubject.class,
                "describe",
                MethodType.methodType(String.class, String.class));
        assertThat(virtualHandle).isNotNull();
        assertThat(virtualHandle.invokeWithArguments(subject, "value")).isEqualTo("value:initial:3");

        MethodHandle staticHandle = MethodHandleUtil.findMethod(
                HandleSubject.class,
                "join",
                MethodType.methodType(String.class, String.class, int.class));
        assertThat(staticHandle).isNotNull();
        assertThat(staticHandle.invokeWithArguments("static", 5)).isEqualTo("static:5");

        MethodHandle specialHandle = MethodHandleUtil.findMethod(
                SpecialGreeting.class,
                "privateGreeting",
                MethodType.methodType(String.class, String.class));
        assertThat(specialHandle).isNotNull();
        assertThat(specialHandle.invokeWithArguments(new SpecialGreetingImpl(), "Ada")).isEqualTo("special Ada");

        MethodHandle constructorHandle = MethodHandleUtil.findConstructor(
                HandleSubject.class,
                MethodType.methodType(void.class, String.class, int.class));
        assertThat(constructorHandle).isNotNull();
        HandleSubject constructed = (HandleSubject) constructorHandle.invokeWithArguments("built", 7);
        assertThat(constructed.describe("made")).isEqualTo("made:built:7");
    }

    @Test
    void performsSpecialLookupForInterfaceDefaultMethod() throws Throwable {
        MethodHandle specialHandle = MethodHandleUtil.lookup(DefaultGreeting.class).findSpecial(
                DefaultGreeting.class,
                "defaultGreeting",
                MethodType.methodType(String.class, String.class),
                DefaultGreeting.class);

        String greeting = (String) specialHandle.invokeWithArguments(new DefaultGreetingImpl(), "Lin");
        assertThat(greeting).isEqualTo("default Lin");
    }

    @Test
    void returnsNullAfterTryingSpecialLookupForMissingMethod() {
        MethodHandle missingHandle = MethodHandleUtil.findMethod(
                HandleSubject.class,
                "missing",
                MethodType.methodType(String.class));

        assertThat(missingHandle).isNull();
    }

    @Test
    void invokesMethodsThroughUnreflectAndUnreflectSpecial() throws Exception {
        HandleSubject subject = new HandleSubject("direct", 11);

        Method describeMethod = HandleSubject.class.getMethod("describe", String.class);
        String described = MethodHandleUtil.invoke(subject, describeMethod, "call");
        assertThat(described).isEqualTo("call:direct:11");

        Method defaultMethod = DefaultGreeting.class.getMethod("defaultGreeting", String.class);
        String defaultGreeting = MethodHandleUtil.invokeSpecial(new DefaultGreetingImpl(), defaultMethod, "Grace");
        assertThat(defaultGreeting).isEqualTo("default Grace");
    }

    public static class HandleSubject {
        private final String value;
        private final int number;

        public HandleSubject(String value, int number) {
            this.value = value;
            this.number = number;
        }

        public String describe(String prefix) {
            return prefix + ":" + value + ":" + number;
        }

        public static String join(String prefix, int number) {
            return prefix + ":" + number;
        }
    }

    public interface SpecialGreeting {
        default String greeting(String name) {
            return privateGreeting(name);
        }

        private String privateGreeting(String name) {
            return "special " + name;
        }
    }

    public static class SpecialGreetingImpl implements SpecialGreeting {
    }

    public interface DefaultGreeting {
        default String defaultGreeting(String name) {
            return "default " + name;
        }
    }

    public static class DefaultGreetingImpl implements DefaultGreeting {
    }
}
