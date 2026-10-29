/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_interceptor.jboss_interceptor_core;

import org.jboss.interceptor.builder.MethodReference;
import org.junit.jupiter.api.Test;
import org_jboss_interceptor.jboss_interceptor_core.InterceptorTestSupport.GreetingService;

import static org.assertj.core.api.Assertions.assertThat;
import static org_jboss_interceptor.jboss_interceptor_core.InterceptorTestSupport.roundTrip;

public class ReflectionUtilsTest {
    @Test
    void restoresAMethodReferenceUsingTheContextClassLoader() throws Exception {
        assertRestoredMethodReference();
    }

    @Test
    void restoresAMethodReferenceWithoutAContextClassLoader() throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader original = thread.getContextClassLoader();
        try {
            thread.setContextClassLoader(null);
            assertRestoredMethodReference();
        } finally {
            thread.setContextClassLoader(original);
        }
    }

    private void assertRestoredMethodReference() throws Exception {
        MethodReference reference = MethodReference.of(
                GreetingService.class.getMethod("greet", String.class), true);

        MethodReference restored = roundTrip(reference, MethodReference.class);

        assertThat(restored).isEqualTo(reference);
        assertThat(restored.getDeclaringClass()).isEqualTo(GreetingService.class);
        assertThat(restored.getParameterTypes()).containsExactly(String.class);
        assertThat(restored.getMethodName()).isEqualTo("greet");
    }
}
