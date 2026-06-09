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
    void defaultInvokerInvokesNoArgumentInstanceMethod() throws Exception {
        InvokedTestMethod target = new InvokedTestMethod();
        Method method = InvokedTestMethod.class.getDeclaredMethod("message");

        Object result = MethodInvoker.DEFAULT_INVOKER.invoke(method, target);

        assertThat(result).isEqualTo("invoked");
        assertThat(target.invoked).isTrue();
    }

    static class InvokedTestMethod {
        private boolean invoked;

        String message() {
            this.invoked = true;
            return "invoked";
        }
    }
}
