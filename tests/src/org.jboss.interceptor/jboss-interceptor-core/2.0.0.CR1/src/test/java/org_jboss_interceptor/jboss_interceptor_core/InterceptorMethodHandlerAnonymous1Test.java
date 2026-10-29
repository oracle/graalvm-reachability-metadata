/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_interceptor.jboss_interceptor_core;

import java.lang.reflect.Method;

import org.jboss.interceptor.builder.InterceptionModelBuilder;
import org.jboss.interceptor.proxy.DefaultInvocationContextFactory;
import org.jboss.interceptor.proxy.DirectClassInterceptorInstantiator;
import org.jboss.interceptor.proxy.InterceptorMethodHandler;
import org.jboss.interceptor.reader.ReflectiveClassMetadata;
import org.jboss.interceptor.spi.metadata.ClassMetadata;
import org.junit.jupiter.api.Test;
import org_jboss_interceptor.jboss_interceptor_core.InterceptorTestSupport.GreetingProxy;
import org_jboss_interceptor.jboss_interceptor_core.InterceptorTestSupport.GreetingService;

import static org.assertj.core.api.Assertions.assertThat;
import static org_jboss_interceptor.jboss_interceptor_core.InterceptorTestSupport.roundTrip;

public class InterceptorMethodHandlerAnonymous1Test {
    @Test
    void restoresTheDefaultHandlerForAPassivatedProxyTarget() throws Throwable {
        GreetingProxy target = new GreetingProxy();
        ClassMetadata<?> metadata = ReflectiveClassMetadata.of(GreetingProxy.class);
        InterceptorMethodHandler handler = new InterceptorMethodHandler(target, metadata,
                InterceptionModelBuilder.<ClassMetadata<?>>newBuilderFor(metadata).build(),
                new DirectClassInterceptorInstantiator(), new DefaultInvocationContextFactory());

        InterceptorMethodHandler restored = roundTrip(handler, InterceptorMethodHandler.class);
        Method method = GreetingService.class.getMethod("greet", String.class);

        assertThat(restored.invoke(null, method, method, new Object[]{"Alex"})).isEqualTo("Hello, Alex");
    }
}
