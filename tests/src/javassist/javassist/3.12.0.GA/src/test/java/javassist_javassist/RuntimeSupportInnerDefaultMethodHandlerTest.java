/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javassist_javassist;

import java.lang.reflect.Method;

import javassist.util.proxy.MethodFilter;
import javassist.util.proxy.ProxyFactory;
import javassist.util.proxy.ProxyObject;
import javassist.util.proxy.RuntimeSupport;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RuntimeSupportInnerDefaultMethodHandlerTest {
    @Test
    void generatedProxyUsesDefaultHandlerToInvokeOriginalMethod() throws Exception {
        ProxyFactory factory = new ProxyFactory();
        factory.setUseCache(false);
        factory.setSuperclass(DefaultHandlerTarget.class);
        factory.setFilter(new MethodFilter() {
            @Override
            public boolean isHandled(Method method) {
                return method.getName().equals("describe");
            }
        });

        try {
            DefaultHandlerTarget proxy = (DefaultHandlerTarget) factory.create(
                    new Class[] { String.class },
                    new Object[] { "created" });

            assertThat(proxy).isInstanceOf(ProxyObject.class);
            assertThat(((ProxyObject) proxy).getHandler()).isSameAs(RuntimeSupport.default_interceptor);
            assertThat(proxy.describe("value")).isEqualTo("created:value");
        } catch (Error error) {
            verifyUnsupportedDynamicClassLoading(error);
        }
    }

    private static void verifyUnsupportedDynamicClassLoading(Error error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error;
        }
    }

    public static class DefaultHandlerTarget {
        private final String prefix;

        public DefaultHandlerTarget(String prefix) {
            this.prefix = prefix;
        }

        public String describe(String value) {
            return prefix + ":" + value;
        }
    }
}
