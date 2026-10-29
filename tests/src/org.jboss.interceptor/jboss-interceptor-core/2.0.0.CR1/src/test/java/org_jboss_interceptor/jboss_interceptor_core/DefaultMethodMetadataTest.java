/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_interceptor.jboss_interceptor_core;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import javax.interceptor.InvocationContext;

import org.jboss.interceptor.proxy.DefaultInvocationContextFactory;
import org.jboss.interceptor.proxy.InterceptorInvocation;
import org.jboss.interceptor.proxy.SimpleInterceptionChain;
import org.jboss.interceptor.reader.ClassMetadataInterceptorReference;
import org.jboss.interceptor.reader.DefaultMethodMetadata;
import org.jboss.interceptor.reader.ReflectiveClassMetadata;
import org.jboss.interceptor.reader.SimpleInterceptorMetadata;
import org.jboss.interceptor.spi.metadata.ClassMetadata;
import org.jboss.interceptor.spi.metadata.MethodMetadata;
import org.jboss.interceptor.spi.model.InterceptionType;
import org.junit.jupiter.api.Test;
import org_jboss_interceptor.jboss_interceptor_core.InterceptorTestSupport.DecoratingInterceptor;
import org_jboss_interceptor.jboss_interceptor_core.InterceptorTestSupport.GreetingService;

import static org.assertj.core.api.Assertions.assertThat;
import static org_jboss_interceptor.jboss_interceptor_core.InterceptorTestSupport.roundTrip;

public class DefaultMethodMetadataTest {
    @Test
    void restoresAnInterceptorMethodForUseInAnInterceptionChain() throws Throwable {
        Method method = DecoratingInterceptor.class.getMethod("decorate", InvocationContext.class);
        MethodMetadata restored = roundTrip(DefaultMethodMetadata.of(method), MethodMetadata.class);
        assertThat(restored.getJavaMethod()).isEqualTo(method);
        assertThat(restored.getSupportedInterceptionTypes()).containsExactly(InterceptionType.AROUND_INVOKE);
        SimpleInterceptorMetadata<ClassMetadata<?>> metadata = new SimpleInterceptorMetadata<>(
                ClassMetadataInterceptorReference.of(
                        ReflectiveClassMetadata.of(DecoratingInterceptor.class)),
                false, Map.of(InterceptionType.AROUND_INVOKE, List.of(restored)));
        GreetingService target = new GreetingService();
        Method business = GreetingService.class.getMethod("greet", String.class);
        SimpleInterceptionChain chain = new SimpleInterceptionChain(List.of(new InterceptorInvocation<>(
                new DecoratingInterceptor(), metadata, InterceptionType.AROUND_INVOKE)),
                InterceptionType.AROUND_INVOKE, target, business);

        assertThat(chain.invokeNextInterceptor(new DefaultInvocationContextFactory()
                .newInvocationContext(chain, target, business, new Object[]{"Kim"}))).isEqualTo("[Hello, Kim]");
    }
}
