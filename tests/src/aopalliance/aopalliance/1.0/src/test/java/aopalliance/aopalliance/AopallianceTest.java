/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package aopalliance.aopalliance;

import org.aopalliance.aop.Advice;
import org.aopalliance.aop.AspectException;
import org.aopalliance.intercept.ConstructorInterceptor;
import org.aopalliance.intercept.ConstructorInvocation;
import org.aopalliance.intercept.Interceptor;
import org.aopalliance.intercept.Invocation;
import org.aopalliance.intercept.Joinpoint;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class AopallianceTest {
    @Test
    void aspectExceptionExposesMessageCauseAndNestedStackTrace() {
        final AspectException messageOnly = new AspectException("advice failed");
        assertThat(messageOnly).hasMessage("advice failed");
        assertThat(messageOnly.getCause()).isNull();

        final IllegalStateException cause = new IllegalStateException("boom");
        final AspectException withCause = new AspectException("wrapped advice failure", cause);
        final StringWriter stackTrace = new StringWriter();
        withCause.printStackTrace(new PrintWriter(stackTrace));

        assertThat(withCause.getCause()).isSameAs(cause);
        assertThat(withCause.getMessage()).isNull();
        assertThat(stackTrace.toString()).contains("java.lang.IllegalStateException: boom");
    }

    @Test
    void methodInterceptorCanInspectMutateAndProceedWithInvocation() throws Throwable {
        final Object target = new Object();
        final CountingMethodInvocation invocation = new CountingMethodInvocation(target, new Object[] {"value", 7},
                arguments -> arguments[0] + ":" + arguments[1]);
        final MethodInterceptor interceptor = methodInvocation -> {
            assertThat(methodInvocation.getThis()).isSameAs(target);
            assertThat(methodInvocation.getStaticPart()).isNull();
            assertThat(methodInvocation.getMethod()).isNull();
            assertThat(methodInvocation.getArguments()).containsExactly("value", 7);

            methodInvocation.getArguments()[0] = "changed";
            return "around(" + methodInvocation.proceed() + ")";
        };

        final Object result = interceptor.invoke(invocation);

        assertThat(interceptor).isInstanceOf(Interceptor.class).isInstanceOf(Advice.class);
        assertThat(invocation).isInstanceOf(Invocation.class).isInstanceOf(Joinpoint.class);
        assertThat(result).isEqualTo("around(changed:7)");
        assertThat(invocation.proceedCalls()).isEqualTo(1);
        assertThat(invocation.getArguments()).containsExactly("changed", 7);
    }

    @Test
    void methodInterceptorCanShortCircuitInvocation() throws Throwable {
        final CountingMethodInvocation invocation = new CountingMethodInvocation(new Object(), new Object[] {"cached"},
                arguments -> "should not be called");
        final MethodInterceptor interceptor = methodInvocation ->
                "short-circuited " + methodInvocation.getArguments()[0];

        final Object result = interceptor.invoke(invocation);

        assertThat(result).isEqualTo("short-circuited cached");
        assertThat(invocation.proceedCalls()).isZero();
    }

    @Test
    void methodInterceptorPropagatesCheckedTargetException() {
        final Exception targetFailure = new Exception("target unavailable");
        final List<String> events = new ArrayList<>();
        final CountingMethodInvocation invocation = new CountingMethodInvocation(new Object(), new Object[] {"command"},
                arguments -> {
                    throw targetFailure;
                });
        final MethodInterceptor interceptor = methodInvocation -> {
            events.add("before");
            try {
                return methodInvocation.proceed();
            } finally {
                events.add("after");
            }
        };

        assertThatThrownBy(() -> interceptor.invoke(invocation)).isSameAs(targetFailure);
        assertThat(events).containsExactly("before", "after");
        assertThat(invocation.proceedCalls()).isEqualTo(1);
    }

    @Test
    void constructorInterceptorCanUseInvocationArguments() throws Throwable {
        final CountingConstructorInvocation invocation = new CountingConstructorInvocation(new Object[] {"service", 3},
                arguments -> new CreatedComponent((String) arguments[0], (Integer) arguments[1]));
        final ConstructorInterceptor interceptor = constructorInvocation -> {
            assertThat(constructorInvocation.getThis()).isNull();
            assertThat(constructorInvocation.getStaticPart()).isNull();
            assertThat(constructorInvocation.getConstructor()).isNull();
            assertThat(constructorInvocation.getArguments()).containsExactly("service", 3);

            constructorInvocation.getArguments()[1] = 4;
            return constructorInvocation.proceed();
        };

        final Object constructed = interceptor.construct(invocation);

        assertThat(interceptor).isInstanceOf(Interceptor.class).isInstanceOf(Advice.class);
        assertThat(invocation).isInstanceOf(Invocation.class).isInstanceOf(Joinpoint.class);
        assertThat(constructed).isInstanceOfSatisfying(CreatedComponent.class, component -> {
            assertThat(component.name()).isEqualTo("service");
            assertThat(component.retries()).isEqualTo(4);
        });
        assertThat(invocation.proceedCalls()).isEqualTo(1);
    }

    @Test
    void constructorInterceptorCanReplaceConstructedObjectAfterProceed() throws Throwable {
        final CountingConstructorInvocation invocation = new CountingConstructorInvocation(new Object[] {"worker", 1},
                arguments -> new CreatedComponent((String) arguments[0], (Integer) arguments[1]));
        final ConstructorInterceptor interceptor = constructorInvocation -> {
            final CreatedComponent constructed = (CreatedComponent) constructorInvocation.proceed();
            return new CreatedComponent(constructed.name() + "-instrumented", constructed.retries() + 2);
        };

        final Object constructed = interceptor.construct(invocation);

        assertThat(constructed).isInstanceOfSatisfying(CreatedComponent.class, component -> {
            assertThat(component.name()).isEqualTo("worker-instrumented");
            assertThat(component.retries()).isEqualTo(3);
        });
        assertThat(invocation.proceedCalls()).isEqualTo(1);
    }

    @Test
    void methodInterceptorChainRunsInNestedOrderAndTransformsResult() throws Throwable {
        final List<String> events = new ArrayList<>();
        final List<MethodInterceptor> interceptors = List.of(
                invocation -> {
                    events.add("first-before");
                    final Object result = invocation.proceed();
                    events.add("first-after");
                    return "[" + result + "]";
                },
                invocation -> {
                    events.add("second-before");
                    final Object result = invocation.proceed();
                    events.add("second-after");
                    return result.toString().toUpperCase(Locale.ROOT);
                }
        );
        final ChainedMethodInvocation invocation = new ChainedMethodInvocation(new Object(), new Object[] {"aop"},
                interceptors, arguments -> {
                    events.add("target");
                    return arguments[0] + " result";
                });

        final Object result = invocation.proceed();

        assertThat(result).isEqualTo("[AOP RESULT]");
        assertThat(events).containsExactly("first-before", "second-before", "target", "second-after", "first-after");
        assertThat(invocation.proceedCalls()).isEqualTo(3);
    }

    private interface InvocationBody {
        Object proceed(Object[] arguments) throws Throwable;
    }

    private static final class CountingMethodInvocation implements MethodInvocation {
        private final Object target;
        private final Object[] arguments;
        private final InvocationBody body;
        private int proceedCalls;

        private CountingMethodInvocation(Object target, Object[] arguments, InvocationBody body) {
            this.target = target;
            this.arguments = arguments;
            this.body = body;
        }

        @Override
        public Method getMethod() {
            return null;
        }

        @Override
        public Object[] getArguments() {
            return arguments;
        }

        @Override
        public Object proceed() throws Throwable {
            proceedCalls++;
            return body.proceed(arguments);
        }

        @Override
        public Object getThis() {
            return target;
        }

        @Override
        public AccessibleObject getStaticPart() {
            return null;
        }

        private int proceedCalls() {
            return proceedCalls;
        }
    }

    private static final class CountingConstructorInvocation implements ConstructorInvocation {
        private final Object[] arguments;
        private final InvocationBody body;
        private int proceedCalls;

        private CountingConstructorInvocation(Object[] arguments, InvocationBody body) {
            this.arguments = arguments;
            this.body = body;
        }

        @Override
        public Constructor getConstructor() {
            return null;
        }

        @Override
        public Object[] getArguments() {
            return arguments;
        }

        @Override
        public Object proceed() throws Throwable {
            proceedCalls++;
            return body.proceed(arguments);
        }

        @Override
        public Object getThis() {
            return null;
        }

        @Override
        public AccessibleObject getStaticPart() {
            return null;
        }

        private int proceedCalls() {
            return proceedCalls;
        }
    }

    private static final class ChainedMethodInvocation implements MethodInvocation {
        private final Object target;
        private final Object[] arguments;
        private final List<MethodInterceptor> interceptors;
        private final InvocationBody body;
        private int index;
        private int proceedCalls;

        private ChainedMethodInvocation(Object target, Object[] arguments, List<MethodInterceptor> interceptors,
                InvocationBody body) {
            this.target = target;
            this.arguments = arguments;
            this.interceptors = interceptors;
            this.body = body;
        }

        @Override
        public Method getMethod() {
            return null;
        }

        @Override
        public Object[] getArguments() {
            return arguments;
        }

        @Override
        public Object proceed() throws Throwable {
            proceedCalls++;
            if (index < interceptors.size()) {
                final MethodInterceptor next = interceptors.get(index++);
                return next.invoke(this);
            }
            return body.proceed(arguments);
        }

        @Override
        public Object getThis() {
            return target;
        }

        @Override
        public AccessibleObject getStaticPart() {
            return null;
        }

        private int proceedCalls() {
            return proceedCalls;
        }
    }

    private static final class CreatedComponent {
        private final String name;
        private final int retries;

        private CreatedComponent(String name, int retries) {
            this.name = name;
            this.retries = retries;
        }

        private String name() {
            return name;
        }

        private int retries() {
            return retries;
        }
    }
}
