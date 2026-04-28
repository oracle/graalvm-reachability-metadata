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
import java.io.Serializable;
import java.lang.reflect.Method;

import javassist.util.proxy.MethodHandler;
import javassist.util.proxy.ProxyFactory;
import javassist.util.proxy.ProxyObjectInputStream;
import javassist.util.proxy.ProxyObjectOutputStream;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ProxyObjectInputStreamTest {
    @Test
    void readsProxyClassDescriptorWithSuperclassAndInterfaces() throws Throwable {
        ProxyFactory factory = new ProxyFactory();
        factory.setUseCache(true);
        factory.setUseWriteReplace(false);
        factory.setSuperclass(DescribedService.class);
        factory.setInterfaces(new Class[] {DescribedContract.class});
        factory.setFilter(method -> method.getName().equals("describe") || method.getName().equals("contract"));

        DescribedService proxy = (DescribedService) factory.create(
                new Class[] {String.class},
                new Object[] {"stored"},
                new DescribingHandler());

        byte[] serialized = serialize(proxy);
        Object deserialized = deserialize(serialized);

        assertThat(ProxyFactory.isProxyClass(deserialized.getClass())).isTrue();
        assertThat(deserialized).isInstanceOf(DescribedService.class);
        assertThat(deserialized).isInstanceOf(DescribedContract.class);

        DescribedService service = (DescribedService) deserialized;
        assertThat(service.value()).isEqualTo("stored");
        assertThat(service.describe()).isEqualTo("handled:stored");
        assertThat(((DescribedContract) service).contract()).isEqualTo("contract:stored");
    }

    private static byte[] serialize(Object value) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ProxyObjectOutputStream output = new ProxyObjectOutputStream(bytes)) {
            output.writeObject(value);
        }
        return bytes.toByteArray();
    }

    private static Object deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ProxyObjectInputStream input = new ProxyObjectInputStream(new ByteArrayInputStream(bytes))) {
            input.setClassLoader(ProxyObjectInputStreamTest.class.getClassLoader());
            return input.readObject();
        }
    }

    public interface DescribedContract {
        String contract();
    }

    public static class DescribedService implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String value;

        public DescribedService(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }

        public String describe() {
            return "base:" + value;
        }
    }

    private static class DescribingHandler implements MethodHandler, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public Object invoke(Object self, Method method, Method proceed, Object[] args) {
            DescribedService service = (DescribedService) self;
            if (method.getName().equals("contract")) {
                return "contract:" + service.value();
            }
            return "handled:" + service.value();
        }
    }
}
