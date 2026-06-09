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
    void defaultInvokerInvokesSuppliedNoArgumentMethod() throws Exception {
        InvocationTarget target = new InvocationTarget("spring");
        Method method = InvocationTarget.class.getDeclaredMethod("message");

        Object result = MethodInvoker.DEFAULT_INVOKER.invoke(method, target);

        assertThat(result).isEqualTo("Hello spring");
        assertThat(target.getInvocationCount()).isEqualTo(1);
    }

    static class InvocationTarget {
        private final String name;

        private int invocationCount;

        InvocationTarget(String name) {
            this.name = name;
        }

        private String message() {
            this.invocationCount++;
            return "Hello " + this.name;
        }

        int getInvocationCount() {
            return this.invocationCount;
        }
    }
}
