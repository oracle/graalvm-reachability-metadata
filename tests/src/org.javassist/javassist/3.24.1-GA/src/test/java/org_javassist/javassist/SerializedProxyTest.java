/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_javassist.javassist;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Method;

import javassist.util.proxy.MethodHandler;
import javassist.util.proxy.ProxyFactory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SerializedProxyTest {
    private ProxyFactory.UniqueName originalNameGenerator;
    private boolean originalUseCache;
    private boolean originalUseWriteReplace;

    @BeforeEach
    void configureProxyFactoryDefaults() {
        originalNameGenerator = ProxyFactory.nameGenerator;
        originalUseCache = ProxyFactory.useCache;
        originalUseWriteReplace = ProxyFactory.useWriteReplace;
        ProxyFactory.nameGenerator = new DeterministicNameGenerator();
        ProxyFactory.useCache = true;
        ProxyFactory.useWriteReplace = true;
    }

    @AfterEach
    void restoreProxyFactoryDefaults() {
        ProxyFactory.nameGenerator = originalNameGenerator;
        ProxyFactory.useCache = originalUseCache;
        ProxyFactory.useWriteReplace = originalUseWriteReplace;
    }

    @Test
    void deserializesWriteReplaceProxyAndRestoresHandler() throws Exception {
        ProxyFactory factory = new ProxyFactory();
        factory.setUseCache(true);
        factory.setUseWriteReplace(true);
        factory.setSuperclass(NoArgGreetingService.class);
        factory.setFilter(method -> method.getName().equals("greet"));

        SerializableMethodHandler handler = new SerializableMethodHandler("restored");
        NoArgGreetingService proxy = (NoArgGreetingService) factory.create(new Class<?>[0], new Object[0], handler);

        assertThat(proxy.greet("Ada")).isEqualTo("restored:Ada");

        NoArgGreetingService restoredProxy = deserialize(serialize(proxy), NoArgGreetingService.class);

        assertThat(restoredProxy).isNotSameAs(proxy);
        assertThat(ProxyFactory.isProxyClass(restoredProxy.getClass())).isTrue();
        assertThat(restoredProxy.greet("Grace")).isEqualTo("restored:Grace");
        assertThat(restoredProxy.name()).isEqualTo("default");
    }

    private static byte[] serialize(Object object) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(object);
        }
        return bytes.toByteArray();
    }

    private static <T> T deserialize(byte[] bytes, Class<T> type) throws Exception {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            Object object = input.readObject();
            assertThat(object).isInstanceOf(type);
            return type.cast(object);
        }
    }

    private static class DeterministicNameGenerator implements ProxyFactory.UniqueName {
        private int counter;

        @Override
        public String get(String classname) {
            return classname + "$$SerializedProxyTest" + counter++;
        }
    }

    public static class NoArgGreetingService implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String name;

        public NoArgGreetingService() {
            this.name = "default";
        }

        public String name() {
            return name;
        }

        public String greet(String recipient) {
            return name + ":" + recipient;
        }
    }

    public static class SerializableMethodHandler implements MethodHandler, Serializable {
        private static final long serialVersionUID = 1L;

        private final String prefix;

        public SerializableMethodHandler(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Object invoke(Object self, Method thisMethod, Method proceed, Object[] args) {
            return prefix + ":" + args[0];
        }
    }
}
