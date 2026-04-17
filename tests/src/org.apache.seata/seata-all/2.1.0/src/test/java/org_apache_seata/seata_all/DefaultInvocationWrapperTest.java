/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_seata.seata_all;

import java.lang.reflect.Method;

import org.apache.seata.integration.tx.api.interceptor.DefaultInvocationWrapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DefaultInvocationWrapperTest {
    @Test
    void proceedInvokesTheDelegateMethodWithTheProvidedArguments() throws Throwable {
        GreetingService delegate = new GreetingService();
        Object proxy = new Object();
        Object[] arguments = new Object[]{"Seata"};
        Method method = GreetingService.class.getMethod("greet", String.class);
        DefaultInvocationWrapper wrapper = new DefaultInvocationWrapper(proxy, delegate, method, arguments);

        assertThat(wrapper.getProxy()).isSameAs(proxy);
        assertThat(wrapper.getTarget()).isSameAs(delegate);
        assertThat(wrapper.getMethod()).isSameAs(method);
        assertThat(wrapper.getArguments()).containsExactly("Seata");
        assertThat(wrapper.proceed()).isEqualTo("hello Seata");
        assertThat(delegate.lastGreetingTarget).isEqualTo("Seata");
    }

    @Test
    void proceedRethrowsTheUnderlyingInvocationCause() throws NoSuchMethodException {
        FailingService delegate = new FailingService();
        Method method = FailingService.class.getMethod("fail");
        DefaultInvocationWrapper wrapper = new DefaultInvocationWrapper(new Object(), delegate, method, new Object[0]);

        assertThatThrownBy(wrapper::proceed)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");
    }

    public static final class GreetingService {
        private String lastGreetingTarget;

        public String greet(String target) {
            lastGreetingTarget = target;
            return "hello " + target;
        }
    }

    public static final class FailingService {
        public void fail() {
            throw new IllegalStateException("boom");
        }
    }
}
