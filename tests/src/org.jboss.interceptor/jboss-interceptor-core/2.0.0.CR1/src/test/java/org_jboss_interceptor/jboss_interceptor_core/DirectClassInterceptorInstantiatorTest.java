/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_interceptor.jboss_interceptor_core;

import org.jboss.interceptor.proxy.DirectClassInterceptorInstantiator;
import org.jboss.interceptor.reader.ClassMetadataInterceptorReference;
import org.jboss.interceptor.reader.ReflectiveClassMetadata;
import org.jboss.interceptor.spi.metadata.ClassMetadata;
import org.jboss.interceptor.spi.metadata.InterceptorReference;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DirectClassInterceptorInstantiatorTest {

    @Test
    void createsInterceptorUsingPublicNoArgConstructor() {
        final ClassMetadata<CountingInterceptor> interceptorMetadata = ReflectiveClassMetadata.of(
                CountingInterceptor.class);
        final InterceptorReference<ClassMetadata<?>> interceptorReference = ClassMetadataInterceptorReference.of(
                interceptorMetadata);
        final DirectClassInterceptorInstantiator instantiator = new DirectClassInterceptorInstantiator();

        final Object interceptor = instantiator.createFor(interceptorReference);

        assertThat(interceptor).isInstanceOf(CountingInterceptor.class);
        assertThat(((CountingInterceptor) interceptor).getInvocationCount()).isZero();
    }

    public static class CountingInterceptor {
        private int invocationCount;

        public int getInvocationCount() {
            return invocationCount;
        }
    }
}
