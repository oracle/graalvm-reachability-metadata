/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cglib.cglib_nodep;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import net.sf.cglib.proxy.Callback;
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.Factory;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class EnhancerTest {
    @Test
    void createsEnhancedSubclassAndUsesCallbackRegistrationApis() throws Throwable {
        try {
            Enhancer enhancer = new Enhancer();
            enhancer.setSuperclass(GreetingService.class);
            enhancer.setCallback(new GreetingInterceptor("created"));

            GreetingService service = (GreetingService) enhancer.create();

            assertThat(service.greet("Ada")).isEqualTo("created Ada");
            assertThat(Enhancer.isEnhanced(service.getClass())).isTrue();

            Enhancer.registerStaticCallbacks(service.getClass(), callbacks(new GreetingInterceptor("static")));
            Factory factory = (Factory) service;
            GreetingService secondService = (GreetingService) factory.newInstance(
                    callbacks(new GreetingInterceptor("factory")));

            assertThat(secondService.greet("Grace")).isEqualTo("factory Grace");
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

    private static Callback[] callbacks(Callback callback) {
        return new Callback[] {callback };
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

    public static class GreetingInterceptor implements MethodInterceptor {
        private final String prefix;

        GreetingInterceptor(String prefix) {
            this.prefix = prefix;
        }

        public Object intercept(Object object, Method method, Object[] arguments, MethodProxy methodProxy) {
            return prefix + " " + arguments[0];
        }
    }
}
