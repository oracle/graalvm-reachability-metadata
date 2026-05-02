/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cglib.cglib_nodep;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import net.sf.cglib.core.Signature;
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class MethodProxyTest {
    @Test
    void findsMethodProxyForEnhancedMethodSignature() throws Throwable {
        try {
            RecordingInterceptor interceptor = new RecordingInterceptor();
            Enhancer enhancer = new Enhancer();
            enhancer.setSuperclass(GreetingService.class);
            enhancer.setCallback(interceptor);

            GreetingService service = (GreetingService) enhancer.create();

            assertThat(service.greet("Ada")).isEqualTo("intercepted hello Ada");

            MethodProxy observedProxy = interceptor.getMethodProxy();
            Signature signature = observedProxy.getSignature();
            MethodProxy foundProxy = MethodProxy.find(service.getClass(), signature);

            assertThat(foundProxy).isNotNull();
            assertThat(foundProxy.getSignature()).isEqualTo(signature);
            assertThat(foundProxy.getSuperName()).isEqualTo(observedProxy.getSuperName());
            assertThat(foundProxy.invokeSuper(service, new Object[] {"Grace" })).isEqualTo("hello Grace");
        } catch (Error error) {
            if (!isUnsupportedNativeImageDynamicClassLoading(error)) {
                throw error;
            }
        } catch (RuntimeException exception) {
            if (!isUnsupportedNativeImageDynamicClassLoading(exception)) {
                throw exception;
            }
        }
    }

    private static boolean isUnsupportedNativeImageDynamicClassLoading(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof Error && NativeImageSupport.isUnsupportedFeatureError((Error) current)) {
                return true;
            }
            if (current instanceof NoClassDefFoundError) {
                String message = current.getMessage();
                if (message != null && message.startsWith("Could not initialize class net.sf.cglib.")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    public static class GreetingService {
        public String greet(String name) {
            return "hello " + name;
        }
    }

    private static final class RecordingInterceptor implements MethodInterceptor {
        private MethodProxy methodProxy;

        public Object intercept(Object object, Method method, Object[] arguments, MethodProxy proxy) throws Throwable {
            methodProxy = proxy;
            return "intercepted " + proxy.invokeSuper(object, arguments);
        }

        MethodProxy getMethodProxy() {
            return methodProxy;
        }
    }
}
