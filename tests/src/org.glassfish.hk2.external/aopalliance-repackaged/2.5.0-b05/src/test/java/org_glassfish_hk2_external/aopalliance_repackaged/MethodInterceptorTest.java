/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_hk2_external.aopalliance_repackaged;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.Test;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class MethodInterceptorTest {
    @Test
    void chainsAdviceAroundADirectMethodCall() throws Throwable {
        final List<String> events = new ArrayList<>();
        final GreetingService target = new GreetingService();
        final MethodInterceptor tracing = invocation -> {
            assertThat(invocation.getThis()).isSameAs(target);
            assertThat(invocation.getMethod().getName()).isEqualTo("greet");
            assertThat(invocation.getStaticPart()).isSameAs(invocation.getMethod());
            events.add("before");
            final Object result = invocation.proceed();
            events.add("after");
            return "[" + result + "]";
        };
        final MethodInterceptor normalization = invocation -> {
            events.add("normalize");
            invocation.getArguments()[0] = ((String) invocation.getArguments()[0]).trim();
            return invocation.proceed();
        };
        final GreetingInvocation invocation = new GreetingInvocation(target, " Ada ", List.of(tracing, normalization));

        assertThat(invocation.proceed()).isEqualTo("[Hello, Ada]");
        assertThat(invocation.getArguments()).containsExactly("Ada");
        assertThat(target.calls).isEqualTo(1);
        assertThat(events).containsExactly("before", "normalize", "after");
    }

    @Test
    void canSupplyACachedResultWithoutCallingTheTarget() throws Throwable {
        final GreetingService target = new GreetingService();
        final MethodInterceptor cache = invocation -> "Cached greeting for " + invocation.getArguments()[0];
        final GreetingInvocation invocation = new GreetingInvocation(target, "Grace", List.of(cache));

        assertThat(invocation.proceed()).isEqualTo("Cached greeting for Grace");
        assertThat(target.calls).isZero();
    }

    @Test
    void letsAdviceObserveAndPropagateATargetException() throws Exception {
        final GreetingService target = new GreetingService();
        final List<String> events = new ArrayList<>();
        final MethodInterceptor tracing = invocation -> {
            events.add("before");
            try {
                return invocation.proceed();
            } finally {
                events.add("after");
            }
        };
        final GreetingInvocation invocation = new GreetingInvocation(target, "", List.of(tracing));

        assertThatThrownBy(invocation::proceed)
                .isInstanceOf(IllegalArgumentException.class).hasMessage("A name is required");
        assertThat(events).containsExactly("before", "after");
    }

    public static final class GreetingService {
        private int calls;

        public String greet(String name) {
            if (name.isEmpty()) {
                throw new IllegalArgumentException("A name is required");
            }
            calls++;
            return "Hello, " + name;
        }
    }

    private static final class GreetingInvocation implements MethodInvocation {
        private final GreetingService target;
        private final Object[] arguments;
        private final List<MethodInterceptor> interceptors;
        private final Method method;
        private int next;

        private GreetingInvocation(GreetingService target, String name, List<MethodInterceptor> interceptors)
                throws NoSuchMethodException {
            this.target = target;
            this.arguments = new Object[] {name};
            this.interceptors = interceptors;
            this.method = GreetingService.class.getMethod("greet", String.class);
        }

        @Override
        public Object proceed() throws Throwable {
            if (next < interceptors.size()) {
                return interceptors.get(next++).invoke(this);
            }
            return target.greet((String) arguments[0]);
        }

        @Override
        public Object[] getArguments() {
            return arguments;
        }

        @Override
        public Object getThis() {
            return target;
        }

        @Override
        public Method getMethod() {
            return method;
        }

        @Override
        public AccessibleObject getStaticPart() {
            return method;
        }
    }
}
