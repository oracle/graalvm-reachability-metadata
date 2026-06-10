/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_test;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

import org.springframework.test.context.MethodInvoker;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultMethodInvokerTest {
    @Test
    void invokesNoArgumentInstanceMethod() throws Exception {
        GreetingTarget target = new GreetingTarget("Spring Test");
        Method method = GreetingTarget.class.getDeclaredMethod("greeting");

        Object result = MethodInvoker.DEFAULT_INVOKER.invoke(method, target);

        assertThat(result).isEqualTo("Hello, Spring Test");
    }

    private static final class GreetingTarget {
        private final String name;

        private GreetingTarget(String name) {
            this.name = name;
        }

        private String greeting() {
            return "Hello, " + this.name;
        }
    }
}
