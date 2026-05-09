/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_interceptor.jboss_interceptor_core;

import java.lang.reflect.Method;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.interceptor.InvocationContext;

import org.jboss.interceptor.proxy.InterceptorInvocation;
import org.jboss.interceptor.proxy.InterceptorInvocationContext;
import org.jboss.interceptor.proxy.SimpleInterceptionChain;
import org.jboss.interceptor.reader.ClassMetadataInterceptorReference;
import org.jboss.interceptor.reader.ReflectiveClassMetadata;
import org.jboss.interceptor.reader.SimpleInterceptorMetadata;
import org.jboss.interceptor.spi.metadata.ClassMetadata;
import org.jboss.interceptor.spi.metadata.InterceptorMetadata;
import org.jboss.interceptor.spi.metadata.InterceptorReference;
import org.jboss.interceptor.spi.metadata.MethodMetadata;
import org.jboss.interceptor.spi.model.InterceptionType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class InterceptorInvocationInnerInterceptorMethodInvocationTest {

    @Test
    void invokesInterceptorMethodWithInvocationContextArgument() throws Throwable {
        final ContextArgumentInterceptor interceptor = new ContextArgumentInterceptor();
        final InterceptorInvocation<?> interceptorInvocation = interceptorInvocation(
                interceptor,
                ContextArgumentInterceptor.class.getMethod("intercept", InvocationContext.class),
                InterceptionType.AROUND_INVOKE);
        final SimpleInterceptionChain chain = new SimpleInterceptionChain(
                List.of(interceptorInvocation),
                InterceptionType.AROUND_INVOKE,
                null,
                null);
        final InterceptorInvocationContext context = new InterceptorInvocationContext(
                null,
                null,
                null,
                new Object[0]);
        context.getContextData().put("message", "context-branch");

        final Object result = chain.invokeNextInterceptor(context);

        assertThat(result).isEqualTo("context-branch");
        assertThat(interceptor.getInvocationCount()).isOne();
    }

    @Test
    void invokesInterceptorMethodWithoutArgumentsForLifecycleCallback() throws Throwable {
        final NoArgumentLifecycleInterceptor interceptor = new NoArgumentLifecycleInterceptor();
        final InterceptorInvocation<?> interceptorInvocation = interceptorInvocation(
                interceptor,
                NoArgumentLifecycleInterceptor.class.getMethod("postConstruct"),
                InterceptionType.POST_CONSTRUCT);
        final SimpleInterceptionChain chain = new SimpleInterceptionChain(
                List.of(interceptorInvocation),
                InterceptionType.POST_CONSTRUCT,
                interceptor,
                null);

        final Object result = chain.invokeNextInterceptor(null);

        assertThat(result).isNull();
        assertThat(interceptor.getInvocationCount()).isOne();
    }

    private static InterceptorInvocation<?> interceptorInvocation(
            Object instance,
            Method method,
            InterceptionType interceptionType
    ) {
        final ClassMetadata<?> classMetadata = ReflectiveClassMetadata.of(instance.getClass());
        final InterceptorReference<ClassMetadata<?>> reference = ClassMetadataInterceptorReference.of(classMetadata);
        final Map<InterceptionType, List<MethodMetadata>> methodMap = new EnumMap<>(InterceptionType.class);
        methodMap.put(interceptionType, List.of(new FixedMethodMetadata(method, interceptionType)));
        final InterceptorMetadata<ClassMetadata<?>> metadata = new SimpleInterceptorMetadata<>(
                reference,
                false,
                methodMap);
        return new InterceptorInvocation<>(instance, metadata, interceptionType);
    }

    public static class ContextArgumentInterceptor {

        private int invocationCount;

        public Object intercept(InvocationContext context) {
            invocationCount++;
            return context.getContextData().get("message");
        }

        int getInvocationCount() {
            return invocationCount;
        }
    }

    public static class NoArgumentLifecycleInterceptor {

        private int invocationCount;

        public void postConstruct() {
            invocationCount++;
        }

        int getInvocationCount() {
            return invocationCount;
        }
    }

    private static final class FixedMethodMetadata implements MethodMetadata {

        private final Method javaMethod;
        private final Set<InterceptionType> supportedInterceptionTypes;

        private FixedMethodMetadata(Method javaMethod, InterceptionType interceptionType) {
            this.javaMethod = javaMethod;
            this.supportedInterceptionTypes = Set.of(interceptionType);
        }

        @Override
        public Method getJavaMethod() {
            return javaMethod;
        }

        @Override
        public Set<InterceptionType> getSupportedInterceptionTypes() {
            return supportedInterceptionTypes;
        }

        @Override
        public Class<?> getReturnType() {
            return javaMethod.getReturnType();
        }
    }
}
