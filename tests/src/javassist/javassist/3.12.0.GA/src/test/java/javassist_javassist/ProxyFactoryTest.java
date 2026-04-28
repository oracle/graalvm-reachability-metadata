/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javassist_javassist;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Method;

import javassist.util.proxy.MethodHandler;
import javassist.util.proxy.ProxyFactory;
import javassist.util.proxy.ProxyObject;
import javassist.util.proxy.ProxyObjectOutputStream;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ProxyFactoryTest {
    @Test
    void createsProxyWithConstructorArgumentsAndSerializesProxyDescriptor() throws Throwable {
        ProxyFactory factory = new StaticProxyFactory();

        GreetingService proxy = (GreetingService) factory.create(
                new Class[] {String.class},
                new Object[] {"original"},
                new GreetingHandler());

        assertThat(ProxyFactory.isProxyClass(proxy.getClass())).isTrue();
        assertThat(proxy.prefix()).isEqualTo("original");
        assertThat(proxy.greet("Javassist")).isEqualTo("handled:Javassist");

        byte[] serialized = serialize(proxy);

        assertThat(serialized).isNotEmpty();
    }

    private static byte[] serialize(Object value) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ProxyObjectOutputStream output = new ProxyObjectOutputStream(bytes)) {
            output.writeObject(value);
        }
        return bytes.toByteArray();
    }

    public static class GreetingService implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String prefix;

        public GreetingService(String prefix) {
            this.prefix = prefix;
        }

        public String prefix() {
            return prefix;
        }

        public String greet(String name) {
            return prefix + ":" + name;
        }
    }

    public static class StaticProxyFactory extends ProxyFactory {
        @Override
        public Class createClass() {
            return StaticGreetingProxy.class;
        }
    }

    public static class StaticGreetingProxy extends GreetingService implements ProxyObject {
        private static final long serialVersionUID = 1L;

        // CheckStyle: start generated
        public static byte[] _filter_signature = new byte[] {1};
        // CheckStyle: stop generated
        private MethodHandler handler;

        public StaticGreetingProxy(String prefix) {
            super(prefix);
        }

        @Override
        public String greet(String name) {
            if (handler == null) {
                return super.greet(name);
            }
            try {
                Method method = GreetingService.class.getMethod("greet", String.class);
                return (String) handler.invoke(this, method, null, new Object[] {name});
            } catch (Throwable throwable) {
                throw new IllegalStateException(throwable);
            }
        }

        @Override
        public void setHandler(MethodHandler mi) {
            handler = mi;
        }

        @Override
        public MethodHandler getHandler() {
            return handler;
        }
    }

    private static class GreetingHandler implements MethodHandler, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public Object invoke(Object self, Method method, Method proceed, Object[] args) {
            return "handled:" + args[0];
        }
    }
}
