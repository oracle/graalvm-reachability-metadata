/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javassist_javassist;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Method;

import javassist.util.proxy.MethodHandler;
import javassist.util.proxy.ProxyFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SerializedProxyTest {
    @Test
    void restoresProxySerializedThroughStandardObjectStreams() throws Throwable {
        ProxyFactory factory = new ProxyFactory();
        factory.setUseCache(false);
        factory.setUseWriteReplace(true);
        factory.setSuperclass(SerializableService.class);
        factory.setInterfaces(new Class[] {TaggedService.class});
        factory.setFilter(method -> method.getName().equals("message") || method.getName().equals("tag"));

        SerializableService proxy = (SerializableService) factory.create(
                new Class[0],
                new Object[0],
                new RestoredHandler());

        Object deserialized = deserialize(serialize(proxy));

        assertThat(ProxyFactory.isProxyClass(deserialized.getClass())).isTrue();
        assertThat(deserialized).isInstanceOf(SerializableService.class);
        assertThat(deserialized).isInstanceOf(TaggedService.class);

        SerializableService service = (SerializableService) deserialized;
        assertThat(service.message()).isEqualTo("restored:message");
        assertThat(service.unhandled()).isEqualTo("base");
        assertThat(((TaggedService) service).tag()).isEqualTo("restored:tag");
    }

    private static byte[] serialize(Object value) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }
        return bytes.toByteArray();
    }

    private static Object deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        Thread currentThread = Thread.currentThread();
        ClassLoader originalClassLoader = currentThread.getContextClassLoader();
        currentThread.setContextClassLoader(SerializedProxyTest.class.getClassLoader());
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return input.readObject();
        } finally {
            currentThread.setContextClassLoader(originalClassLoader);
        }
    }

    public interface TaggedService {
        String tag();
    }

    public static class SerializableService implements Serializable {
        private static final long serialVersionUID = 1L;

        public SerializableService() {
        }

        public String message() {
            return "original";
        }

        public String unhandled() {
            return "base";
        }
    }

    private static class RestoredHandler implements MethodHandler, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public Object invoke(Object self, Method method, Method proceed, Object[] args) {
            return "restored:" + method.getName();
        }
    }
}
