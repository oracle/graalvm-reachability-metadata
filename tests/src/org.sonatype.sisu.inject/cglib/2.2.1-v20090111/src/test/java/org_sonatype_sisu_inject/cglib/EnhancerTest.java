/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_sonatype_sisu_inject.cglib;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class EnhancerTest {

    @Test
    void createsInterceptedSubclassAndRegistersCallback() throws Throwable {
        try {
            Enhancer enhancer = new Enhancer();
            enhancer.setSuperclass(GreetingTarget.class);
            enhancer.setCallback(new PrefixingInterceptor());

            GreetingTarget enhanced = (GreetingTarget) enhancer.create();

            assertThat(enhanced.greet("Native Image")).isEqualTo("intercepted: hello Native Image");
            assertThat(Enhancer.isEnhanced(enhanced.getClass())).isTrue();
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    public static class GreetingTarget {
        public String greet(String name) {
            return "hello " + name;
        }
    }

    public static final class PrefixingInterceptor implements MethodInterceptor {
        @Override
        public Object intercept(Object object, Method method, Object[] arguments, MethodProxy methodProxy)
                throws Throwable {
            return "intercepted: " + methodProxy.invokeSuper(object, arguments);
        }
    }
}
