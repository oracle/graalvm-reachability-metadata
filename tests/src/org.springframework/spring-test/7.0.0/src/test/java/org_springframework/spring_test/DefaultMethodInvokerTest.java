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
    void invokesAccessibleMethodThroughDefaultInvoker() throws Exception {
        InvokedTestCase target = new InvokedTestCase();
        Method method = InvokedTestCase.class.getDeclaredMethod("recordInvocation");

        Object result = MethodInvoker.DEFAULT_INVOKER.invoke(method, target);

        assertThat(result).isEqualTo("invoked-1");
        assertThat(target.getInvocationCount()).isEqualTo(1);
    }

    static class InvokedTestCase {
        private int invocationCount;

        private String recordInvocation() {
            this.invocationCount++;
            return "invoked-" + this.invocationCount;
        }

        int getInvocationCount() {
            return this.invocationCount;
        }
    }
}
