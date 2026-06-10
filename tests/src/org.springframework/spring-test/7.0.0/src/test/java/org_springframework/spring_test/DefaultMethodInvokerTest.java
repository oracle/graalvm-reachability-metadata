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
    void invokesNoArgumentMethodThroughDefaultInvoker() throws Exception {
        Method method = SampleTarget.class.getDeclaredMethod("message");
        SampleTarget target = new SampleTarget("spring-test");

        Object result = MethodInvoker.DEFAULT_INVOKER.invoke(method, target);

        assertThat(result).isEqualTo("invoked spring-test");
        assertThat(target.invocationCount).isEqualTo(1);
    }

    public static class SampleTarget {
        private final String value;

        private int invocationCount;

        SampleTarget(String value) {
            this.value = value;
        }

        String message() {
            this.invocationCount++;
            return "invoked " + this.value;
        }
    }
}
