/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_seata.seata_all;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;

import org.apache.seata.integration.tx.api.interceptor.DefaultInvocationWrapper;
import org.junit.jupiter.api.Test;

public class DefaultInvocationWrapperTest {
    @Test
    void proceedsByInvokingWrappedMethodOnDelegate() throws Throwable {
        InvocationService delegate = new InvocationService();
        Object proxy = new Object();
        Object[] arguments = {"seata", 2};
        Method method = InvocationService.class.getMethod("repeat", String.class, int.class);
        DefaultInvocationWrapper wrapper = new DefaultInvocationWrapper(proxy, delegate, method, arguments);

        Object result = wrapper.proceed();

        assertThat(result).isEqualTo("seataseata");
        assertThat(delegate.getInvocationCount()).isEqualTo(1);
        assertThat(wrapper.getProxy()).isSameAs(proxy);
        assertThat(wrapper.getTarget()).isSameAs(delegate);
        assertThat(wrapper.getMethod()).isSameAs(method);
        assertThat(wrapper.getArguments()).isSameAs(arguments);
    }

    @Test
    void unwrapsExceptionThrownByWrappedMethod() throws Exception {
        InvocationService delegate = new InvocationService();
        Method method = InvocationService.class.getMethod("failWithIllegalState");
        DefaultInvocationWrapper wrapper = new DefaultInvocationWrapper(new Object(), delegate, method, new Object[0]);

        assertThatThrownBy(wrapper::proceed)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("expected failure");
    }

    public static class InvocationService {
        private int invocationCount;

        public String repeat(String value, int times) {
            invocationCount++;
            return value.repeat(times);
        }

        public void failWithIllegalState() {
            throw new IllegalStateException("expected failure");
        }

        public int getInvocationCount() {
            return invocationCount;
        }
    }
}
