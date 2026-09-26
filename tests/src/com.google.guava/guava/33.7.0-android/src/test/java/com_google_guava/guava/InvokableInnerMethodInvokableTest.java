/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_guava.guava;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.reflect.Invokable;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

public class InvokableInnerMethodInvokableTest {
    @Test
    void invokeDispatchesThroughWrappedMethod() throws Exception {
        Method method = GreetingTarget.class.getMethod("formatGreeting", String.class, int.class);
        @SuppressWarnings("unchecked")
        Invokable<Object, Object> invokable = (Invokable<Object, Object>) Invokable.from(method);
        GreetingTarget target = new GreetingTarget("Hello");

        Object greeting = invokable.invoke(target, "Guava", 33);

        assertThat(greeting).isEqualTo("Hello, Guava 33");
        assertThat(target.invocationCount()).isEqualTo(1);
        assertThat(invokable.getReturnType().getRawType()).isEqualTo(String.class);
        assertThat(invokable.isOverridable()).isTrue();
    }

    public static class GreetingTarget {
        private final String salutation;
        private int invocationCount;

        public GreetingTarget(String salutation) {
            this.salutation = salutation;
        }

        public String formatGreeting(String name, int version) {
            invocationCount++;
            return salutation + ", " + name + " " + version;
        }

        int invocationCount() {
            return invocationCount;
        }
    }
}
