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
        Method method = GreetingOperations.class.getMethod("greeting", String.class);

        MethodHandle methodHandle = DefaultMethods.lookupMethodHandle(method);
        Object result = methodHandle.bindTo(new GreetingTarget()).invokeWithArguments("lettuce");

        assertThat(result).isEqualTo("Hello lettuce");
    }

    public interface GreetingOperations {

        default String greeting(String name) {
            return "Hello " + name;
        }
    }

    public static final class GreetingTarget implements GreetingOperations {
    }
}
