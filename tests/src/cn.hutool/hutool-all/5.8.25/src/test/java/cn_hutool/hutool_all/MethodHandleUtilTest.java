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
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

public class MethodHandleUtilTest {
    @Test
    public void findsConstructorsVirtualMethodsStaticMethodsAndSpecialMethods() throws Throwable {
        MethodHandle constructorHandle = MethodHandleUtil.findConstructor(
                MethodHandleSubject.class, String.class, int.class);
        assertThat(constructorHandle).isNotNull();

        MethodHandleSubject subject = (MethodHandleSubject) constructorHandle.invokeWithArguments("subject", 7);
        assertThat(subject).isNotNull();

        MethodHandle virtualHandle = MethodHandleUtil.findMethod(
                MethodHandleSubject.class,
                "instanceMessage",
                MethodType.methodType(String.class, String.class));
        assertThat(virtualHandle).isNotNull();
        assertThat((String) virtualHandle.invokeWithArguments(subject, "virtual"))
                .isEqualTo("subject:virtual:7");

        MethodHandle staticHandle = MethodHandleUtil.findMethod(
                MethodHandleSubject.class,
                "staticMessage",
                MethodType.methodType(String.class, String.class));
        assertThat(staticHandle).isNotNull();
        assertThat((String) staticHandle.invokeWithArguments("static"))
                .isEqualTo("handled-static");

        MethodHandle specialHandle = MethodHandleUtil.findMethod(
                MethodHandleSubject.class,
                "specialMessage",
                MethodType.methodType(String.class, String.class));
        assertThat(specialHandle).isNotNull();
        assertThat((String) specialHandle.invokeWithArguments(subject, "special"))
                .isEqualTo("subject:special:14");
    }

    @Test
    public void invokesMethodsThroughUnreflectAndUnreflectSpecial() throws Exception {
        Method lookupMethod = MethodHandleUtil.class.getMethod("lookup", Class.class);
        MethodHandles.Lookup lookup = MethodHandleUtil.invoke(null, lookupMethod, MethodHandleUtil.class);
        assertThat(lookup.lookupClass()).isEqualTo(MethodHandleUtil.class);

        Method greetingMethod = DefaultGreeting.class.getMethod("greeting", String.class);
        String greeting = MethodHandleUtil.invokeSpecial(new GreetingSubject(), greetingMethod, "Hutool");
        assertThat(greeting).isEqualTo("hello Hutool");
    }

    public static class MethodHandleSubject {
        private final String prefix;
        private final int count;

        public MethodHandleSubject(String prefix, int count) {
            this.prefix = prefix;
            this.count = count;
        }

        public String instanceMessage(String suffix) {
            return prefix + ":" + suffix + ":" + count;
        }

        public static String staticMessage(String suffix) {
            return "handled-" + suffix;
        }

        private String specialMessage(String suffix) {
            return prefix + ":" + suffix + ":" + (count * 2);
        }
    }

    public interface DefaultGreeting {
        String greetingPrefix();

        default String greeting(String name) {
            return greetingPrefix() + " " + name;
        }
    }

    public static class GreetingSubject implements DefaultGreeting {
        @Override
        public String greetingPrefix() {
            return "hello";
        }
    }
}
