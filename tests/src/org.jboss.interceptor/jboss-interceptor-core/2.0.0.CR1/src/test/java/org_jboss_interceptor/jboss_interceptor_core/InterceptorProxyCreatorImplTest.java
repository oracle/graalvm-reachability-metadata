/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_interceptor.jboss_interceptor_core;

import org.jboss.interceptor.builder.InterceptionModelBuilder;
import org.jboss.interceptor.proxy.DefaultInvocationContextFactory;
import org.jboss.interceptor.proxy.DirectClassInterceptorInstantiator;
import org.jboss.interceptor.proxy.InterceptorProxyCreatorImpl;
import org.jboss.interceptor.reader.ReflectiveClassMetadata;
import org.jboss.interceptor.spi.metadata.ClassMetadata;
import org.junit.jupiter.api.Test;
import org_jboss_interceptor.jboss_interceptor_core.InterceptorTestSupport.DecoratingInterceptor;
import org_jboss_interceptor.jboss_interceptor_core.InterceptorTestSupport.GreetingProxy;
import org_jboss_interceptor.jboss_interceptor_core.InterceptorTestSupport.GreetingService;

import static org.assertj.core.api.Assertions.assertThat;
import static org_jboss_interceptor.jboss_interceptor_core.InterceptorTestSupport.interceptorMetadata;

public class InterceptorProxyCreatorImplTest {
    @Test
    void createsAStaticProxyWithAnInterceptedBusinessMethod() throws Throwable {
        ClassMetadata<?> metadata = ReflectiveClassMetadata.of(GreetingService.class);
        InterceptionModelBuilder<ClassMetadata<?>, ?> builder = InterceptionModelBuilder.newBuilderFor(metadata);
        builder.interceptAroundInvoke(GreetingService.class.getMethod("greet", String.class))
                .with(interceptorMetadata(DecoratingInterceptor.class));
        InterceptorProxyCreatorImpl creator = new InterceptorProxyCreatorImpl(
                new DirectClassInterceptorInstantiator(), new DefaultInvocationContextFactory(), builder.build());
        GreetingService target = new GreetingService();
        GreetingProxy proxy = creator.createProxyInstance(
                GreetingProxy.class, creator.createMethodHandler(target, metadata));

        assertThat(proxy.getHandler()).isNotNull();
        assertThat(proxy.greet("Ada")).isEqualTo("[Hello, Ada]");
    }
}
