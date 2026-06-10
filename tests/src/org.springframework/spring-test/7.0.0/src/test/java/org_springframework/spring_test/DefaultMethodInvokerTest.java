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
    void invokesNoArgumentInstanceMethodWithDefaultMethodInvoker() throws Exception {
        InvokedTestCase testCase = new InvokedTestCase();
        Method method = InvokedTestCase.class.getMethod("markInvoked");

        Object result = MethodInvoker.DEFAULT_INVOKER.invoke(method, testCase);

        assertThat(result).isEqualTo("invoked");
        assertThat(testCase.isInvoked()).isTrue();
    }

    public static class InvokedTestCase {
        private boolean invoked;

        public String markInvoked() {
            this.invoked = true;
            return "invoked";
        }

        boolean isInvoked() {
            return this.invoked;
        }
    }
}
