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
import org.springframework.cglib.proxy.Enhancer;
import org.springframework.cglib.proxy.MethodInterceptor;

public class BridgeMethodResolverTest {

    @Test
    void enhancerResolvesBridgeMethodTargetsFromDeclaringClassResource() {
        MethodInterceptor interceptor = (object, method, arguments, methodProxy) -> {
            Object result = methodProxy.invokeSuper(object, arguments);
            if ("store".equals(method.getName())) {
                return "intercepted " + result;
            }
            return result;
        };

        try {
            Enhancer enhancer = new Enhancer();
            enhancer.setSuperclass(StringStore.class);
            enhancer.setCallback(interceptor);

            GenericStore<String> proxy = (GenericStore<String>) enhancer.create();

            assertThat(proxy.store("Spring")).isEqualTo("intercepted stored Spring");
        }
        catch (IllegalArgumentException ex) {
            assertThat(ex).hasMessageStartingWith("Unsupported class file major version");
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

    public static class GenericStore<T> {

        public T store(T value) {
            return value;
        }
    }

    public static class StringStore extends GenericStore<String> {

        @Override
        public String store(String value) {
            return "stored " + value;
        }
    }
}
