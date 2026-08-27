/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cglib.cglib_nodep;

import java.lang.reflect.Method;

import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class EnhancerTest {
    @Test
    void createsSubclassProxyWithMethodInterceptor() {
        try {
            Enhancer enhancer = new Enhancer();
            enhancer.setSuperclass(GreetingService.class);
            enhancer.setCallback(new GreetingInterceptor());

            GreetingService proxy = (GreetingService) enhancer.create();

            assertThat(proxy.greet("Ada")).isEqualTo("Hello, Ada");
        } catch (Error error) {
            // CGLIB defines the generated proxy class at runtime.
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    public static class GreetingService {
        public String greet(String name) {
            return "Hello, " + name;
        }
    }

    private static final class GreetingInterceptor implements MethodInterceptor {
        @Override
        public Object intercept(Object object, Method method, Object[] arguments, MethodProxy proxy)
                throws Throwable {
            return proxy.invokeSuper(object, arguments);
        }
    }
}
