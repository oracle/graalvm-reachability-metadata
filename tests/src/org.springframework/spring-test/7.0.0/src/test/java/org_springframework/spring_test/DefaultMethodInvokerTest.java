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
    void defaultInvokerInvokesZeroArgumentInstanceMethod() throws Exception {
        InvokedComponent component = new InvokedComponent();
        Method method = InvokedComponent.class.getDeclaredMethod("recordInvocation");

        Object result = MethodInvoker.DEFAULT_INVOKER.invoke(method, component);

        assertThat(result).isEqualTo("called");
        assertThat(component.wasInvoked()).isTrue();
    }

    public static class InvokedComponent {
        private boolean invoked;

        public String recordInvocation() {
            this.invoked = true;
            return "called";
        }

        boolean wasInvoked() {
            return this.invoked;
        }
    }
}
