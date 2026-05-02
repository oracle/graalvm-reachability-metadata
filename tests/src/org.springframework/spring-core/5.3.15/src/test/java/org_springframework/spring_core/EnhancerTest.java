/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.springframework.cglib.core.CodeGenerationException;
import org.springframework.cglib.proxy.Callback;
import org.springframework.cglib.proxy.Enhancer;
import org.springframework.cglib.proxy.Factory;
import org.springframework.cglib.proxy.MethodInterceptor;

public class EnhancerTest {

    @Test
    void createsCachedSubclassAndRegistersCallbacks() {
        MethodInterceptor greetingInterceptor = (object, method, arguments, methodProxy) -> {
            Object result = methodProxy.invokeSuper(object, arguments);
            if ("greet".equals(method.getName())) {
                return "intercepted " + result;
            }
            return result;
        };
        MethodInterceptor replacementInterceptor = (object, method, arguments, methodProxy) -> {
            Object result = methodProxy.invokeSuper(object, arguments);
            if ("greet".equals(method.getName())) {
                return "replacement " + result;
            }
            return result;
        };

        try {
            Enhancer enhancer = new Enhancer();
            enhancer.setSuperclass(GreetingService.class);
            enhancer.setCallback(greetingInterceptor);

            GreetingService proxy = (GreetingService) enhancer.create();
            Class<?> generatedClass = proxy.getClass();

            assertThat(proxy.greet("Spring")).isEqualTo("intercepted hello Spring");
            assertThat(Enhancer.isEnhanced(generatedClass)).isTrue();
            assertThatCode(() -> Enhancer.registerCallbacks(
                    generatedClass,
                    new Callback[] {replacementInterceptor}
            )).doesNotThrowAnyException();
            assertThatCode(() -> Enhancer.registerStaticCallbacks(
                    generatedClass,
                    new Callback[] {greetingInterceptor}
            )).doesNotThrowAnyException();

            GreetingService replacementProxy = (GreetingService) ((Factory) proxy).newInstance(
                    new Callback[] {replacementInterceptor}
            );
            assertThat(replacementProxy.greet("Core")).isEqualTo("replacement hello Core");
        }
        catch (CodeGenerationException ex) {
            ignoreUnsupportedDynamicClassLoading(ex);
        }
        catch (Error error) {
            ignoreUnsupportedDynamicClassLoading(error);
        }
    }

    private static void ignoreUnsupportedDynamicClassLoading(CodeGenerationException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof Error && NativeImageSupport.isUnsupportedFeatureError((Error) cause)) {
            return;
        }
        throw ex;
    }

    private static void ignoreUnsupportedDynamicClassLoading(Error error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error;
        }
    }

    public static class GreetingService {

        public String greet(String name) {
            return "hello " + name;
        }
    }
}
