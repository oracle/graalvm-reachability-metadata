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
    void invokesMethodAndReturnsItsResult() throws Exception {
        GreetingTarget target = new GreetingTarget();
        Method method = GreetingTarget.class.getMethod("greet");

        Object result = MethodInvoker.DEFAULT_INVOKER.invoke(method, target);

        assertThat(result).isEqualTo("hello");
        assertThat(target.getInvocationCount()).isEqualTo(1);
    }

    public static class GreetingTarget {
        private int invocationCount;

        public String greet() {
            this.invocationCount++;
            return "hello";
        }

        int getInvocationCount() {
            return this.invocationCount;
        }
    }
}
