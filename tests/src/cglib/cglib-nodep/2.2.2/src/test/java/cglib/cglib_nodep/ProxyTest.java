/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cglib.cglib_nodep;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import net.sf.cglib.proxy.InvocationHandler;
import net.sf.cglib.proxy.Proxy;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class ProxyTest {
    @Test
    void createsProxyInstanceWithInvocationHandler() {
        try {
            Map<Object, Object> target = new HashMap<Object, Object>();
            target.put("language", "java");
            InvocationHandler handler = new MapInvocationHandler(target);

            Object proxy = Proxy.newProxyInstance(
                    ProxyTest.class.getClassLoader(),
                    new Class[] {Map.class },
                    handler);

            assertThat(proxy).isInstanceOf(Map.class);
            assertThat(Proxy.isProxyClass(proxy.getClass())).isTrue();
            assertThat(Proxy.getInvocationHandler(proxy)).isSameAs(handler);

            @SuppressWarnings("unchecked")
            Map<Object, Object> proxyMap = (Map<Object, Object>) proxy;
            assertThat(proxyMap.get("language")).isEqualTo("java");
            assertThat(proxyMap.put("library", "cglib")).isNull();
            assertThat(proxyMap.containsKey("library")).isTrue();
            assertThat(proxyMap.size()).isEqualTo(2);
            assertThat(proxyMap.toString()).contains("language");
            assertThat(target).containsEntry("library", "cglib");
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

    private static final class MapInvocationHandler implements InvocationHandler {
        private final Map<Object, Object> target;

        MapInvocationHandler(Map<Object, Object> target) {
            this.target = target;
        }

        public Object invoke(Object proxy, Method method, Object[] arguments) {
            String methodName = method.getName();
            if ("get".equals(methodName)) {
                return target.get(arguments[0]);
            }
            if ("put".equals(methodName)) {
                return target.put(arguments[0], arguments[1]);
            }
            if ("containsKey".equals(methodName)) {
                return Boolean.valueOf(target.containsKey(arguments[0]));
            }
            if ("size".equals(methodName)) {
                return Integer.valueOf(target.size());
            }
            if ("toString".equals(methodName)) {
                return "cglib proxy " + target;
            }
            if ("hashCode".equals(methodName)) {
                return Integer.valueOf(System.identityHashCode(proxy));
            }
            if ("equals".equals(methodName)) {
                return Boolean.valueOf(proxy == arguments[0]);
            }
            throw new UnsupportedOperationException(method.toString());
        }
    }
}
