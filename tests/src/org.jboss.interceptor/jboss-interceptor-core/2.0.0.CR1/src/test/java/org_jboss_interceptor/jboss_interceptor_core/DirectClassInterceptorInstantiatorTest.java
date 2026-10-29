/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_interceptor.jboss_interceptor_core;

import java.lang.reflect.Method;
import java.util.List;

import org.jboss.interceptor.proxy.DefaultInvocationContextFactory;
import org.jboss.interceptor.proxy.DirectClassInterceptorInstantiator;
import org.jboss.interceptor.proxy.InterceptorInvocation;
import org.jboss.interceptor.proxy.SimpleInterceptionChain;
import org.jboss.interceptor.reader.ClassMetadataInterceptorReference;
import org.jboss.interceptor.reader.ReflectiveClassMetadata;
import org.jboss.interceptor.spi.model.InterceptionType;
import org.junit.jupiter.api.Test;
import org_jboss_interceptor.jboss_interceptor_core.InterceptorTestSupport.DecoratingInterceptor;
import org_jboss_interceptor.jboss_interceptor_core.InterceptorTestSupport.GreetingService;

import static org.assertj.core.api.Assertions.assertThat;
import static org_jboss_interceptor.jboss_interceptor_core.InterceptorTestSupport.interceptorMetadata;

public class DirectClassInterceptorInstantiatorTest {
    @Test
    void instantiatesAnInterceptorThatCanDecorateAnInvocation() throws Throwable {
        DirectClassInterceptorInstantiator instantiator = new DirectClassInterceptorInstantiator();
        Object interceptor = instantiator.createFor(ClassMetadataInterceptorReference.of(
                ReflectiveClassMetadata.of(DecoratingInterceptor.class)));
        Object second = instantiator.createFor(ClassMetadataInterceptorReference.of(
                ReflectiveClassMetadata.of(DecoratingInterceptor.class)));
        assertThat(interceptor).isInstanceOf(DecoratingInterceptor.class).isNotSameAs(second);

        GreetingService target = new GreetingService();
        Method method = GreetingService.class.getMethod("greet", String.class);
        SimpleInterceptionChain chain = new SimpleInterceptionChain(List.of(new InterceptorInvocation<>(interceptor,
                interceptorMetadata(DecoratingInterceptor.class), InterceptionType.AROUND_INVOKE)),
                InterceptionType.AROUND_INVOKE, target, method);

        assertThat(chain.invokeNextInterceptor(new DefaultInvocationContextFactory()
                .newInvocationContext(chain, target, method, new Object[]{"Jo"}))).isEqualTo("[Hello, Jo]");
    }
}
