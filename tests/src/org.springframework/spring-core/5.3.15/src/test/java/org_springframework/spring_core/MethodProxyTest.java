/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.springframework.cglib.core.CodeGenerationException;
import org.springframework.cglib.core.Signature;
import org.springframework.cglib.proxy.Enhancer;
import org.springframework.cglib.proxy.MethodInterceptor;
import org.springframework.cglib.proxy.MethodProxy;

public class MethodProxyTest {

    @Test
    void findsProxyForGeneratedMethodSignature() {
        MethodInterceptor interceptor = (object, method, arguments, methodProxy) ->
                methodProxy.invokeSuper(object, arguments);

        try {
            Enhancer enhancer = new Enhancer();
            enhancer.setSuperclass(GreetingService.class);
            enhancer.setCallback(interceptor);

            GreetingService proxy = (GreetingService) enhancer.create();
            Signature signature = new Signature("greet", "(Ljava/lang/String;)Ljava/lang/String;");

            MethodProxy methodProxy = MethodProxy.find(proxy.getClass(), signature);

            assertThat(methodProxy).isNotNull();
            assertThat(methodProxy.getSignature()).isEqualTo(signature);
            assertThat(methodProxy.getSuperName()).isNotEmpty();
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
