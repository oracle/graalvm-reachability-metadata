/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.springframework.cglib.core.ReflectUtils;
import org.springframework.cglib.proxy.Callback;
import org.springframework.cglib.proxy.Enhancer;
import org.springframework.cglib.proxy.MethodInterceptor;
import org.springframework.cglib.proxy.MethodProxy;

/** Verifies CGLIB subclass generation and callback registration. §FS-repository-functional-spec.5.2 */
public class EnhancerTest {
    @Test
    void generatesSubclassAndRegistersCallbacksForReflectiveConstruction() {
        try {
            Enhancer enhancer = new Enhancer();
            enhancer.setSuperclass(GreetingService.class);
            enhancer.setCallbackType(MethodInterceptor.class);

            Class<?> enhancedClass = enhancer.createClass();
            Callback[] callbacks = new Callback[] {new GreetingInterceptor()};
            GreetingService greetingService;
            Enhancer.registerCallbacks(enhancedClass, callbacks);
            try {
                greetingService = (GreetingService) ReflectUtils.newInstance(enhancedClass);
            } finally {
                Enhancer.registerCallbacks(enhancedClass, null);
            }

            assertThat(Enhancer.isEnhanced(enhancedClass)).isTrue();
            assertThat(greetingService.greet("Spring")).isEqualTo("Enhanced Spring");
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    public static class GreetingService {
        public String greet(String name) {
            return "Hello " + name;
        }
    }

    public static class GreetingInterceptor implements MethodInterceptor {
        @Override
        public Object intercept(Object object, Method method, Object[] arguments, MethodProxy proxy) {
            return "Enhanced " + arguments[0];
        }
    }
}
