/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_auto_value.auto_value;

import static org.assertj.core.api.Assertions.assertThat;

import autovalue.shaded.com.google.common.reflect.Reflection;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

public class ReflectionTest {
    @Test
    void initializesClassesAndCreatesInterfaceProxy() {
        Reflection.initialize(Initialized.class);
        Greeter greeter = Reflection.newProxy(Greeter.class, new GreeterHandler());

        assertThat(Initialized.VALUE).isEqualTo("initialized");
        assertThat(greeter.greet("AutoValue")).isEqualTo("Hello AutoValue");
    }

    public interface Greeter {
        String greet(String name);
    }

    public static final class Initialized {
        static final String VALUE = initialize();

        private static String initialize() {
            return "initialized";
        }
    }

    private static final class GreeterHandler implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) {
            return "Hello " + arguments[0];
        }
    }
}
