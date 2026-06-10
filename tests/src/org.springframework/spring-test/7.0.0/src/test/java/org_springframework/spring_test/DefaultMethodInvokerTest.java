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
    void invokesNoArgumentInstanceMethodThroughDefaultInvoker() throws Exception {
        InvocationTarget target = new InvocationTarget("spring-test");
        Method method = InvocationTarget.class.getDeclaredMethod("message");

        Object result = MethodInvoker.DEFAULT_INVOKER.invoke(method, target);

        assertThat(result).isEqualTo("invoked spring-test");
        assertThat(target.wasInvoked()).isTrue();
    }

    public static class InvocationTarget {
        private final String value;

        private boolean invoked;

        InvocationTarget(String value) {
            this.value = value;
        }

        public String message() {
            this.invoked = true;
            return "invoked " + this.value;
        }

        boolean wasInvoked() {
            return this.invoked;
        }
    }
}
