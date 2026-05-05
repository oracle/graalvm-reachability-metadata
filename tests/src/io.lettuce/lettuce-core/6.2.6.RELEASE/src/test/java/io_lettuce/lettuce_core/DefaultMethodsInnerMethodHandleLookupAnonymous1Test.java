/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_lettuce.lettuce_core;

import static org.assertj.core.api.Assertions.assertThat;

import io.lettuce.core.internal.DefaultMethods;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

public class DefaultMethodsInnerMethodHandleLookupAnonymous1Test {

    @Test
    void lookupMethodHandleInvokesDefaultInterfaceMethod() throws Throwable {
        Method method = Greeting.class.getMethod("greet", String.class);
        assertThat(method.isDefault()).isTrue();

        MethodHandle methodHandle = DefaultMethods.lookupMethodHandle(method);
        Object result = methodHandle.bindTo(new GreetingImpl("Ada")).invokeWithArguments("hello");

        assertThat(result).isEqualTo("hello Ada");
    }

    public interface Greeting {

        default String greet(String prefix) {
            return prefix + " " + name();
        }

        String name();
    }

    public static final class GreetingImpl implements Greeting {

        private final String name;

        GreetingImpl(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }
    }
}
