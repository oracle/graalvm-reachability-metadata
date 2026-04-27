/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_javassist.javassist;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import javassist.util.proxy.MethodHandler;
import javassist.util.proxy.ProxyFactory;
import javassist.util.proxy.ProxyObjectOutputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ProxyObjectOutputStreamTest {
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
    void writesProxyClassDescriptorForSuperclassAndCustomInterface() throws Exception {
        ProxyFactory factory = new ProxyFactory();
        factory.setUseCache(false);
        factory.setUseWriteReplace(false);
        factory.setSuperclass(GreetingTarget.class);
        factory.setInterfaces(new Class<?>[] {DescribedGreeting.class});
        factory.setFilter(method -> method.getName().equals("describe"));

        SerializableMethodHandler handler = new SerializableMethodHandler("proxied");
        Object proxy = factory.create(new Class<?>[] {String.class}, new Object[] {"Ada"}, handler);

        assertThat(((GreetingTarget) proxy).name()).isEqualTo("Ada");
        assertThat(((DescribedGreeting) proxy).describe("Grace")).isEqualTo("proxied:Grace");

        byte[] serializedProxy = serializeWithProxyObjectOutputStream(proxy);
        String serializedText = new String(serializedProxy, StandardCharsets.ISO_8859_1);

        assertThat(serializedProxy).isNotEmpty();
        assertThat(serializedText).contains(GreetingTarget.class.getName());
        assertThat(serializedText).contains(DescribedGreeting.class.getName());
    }

    private static byte[] serializeWithProxyObjectOutputStream(Object object) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ProxyObjectOutputStream output = new ProxyObjectOutputStream(bytes)) {
            output.writeObject(object);
        }
        return bytes.toByteArray();
    }

    private static class DeterministicNameGenerator implements ProxyFactory.UniqueName {
        private int counter;

        @Override
        public String get(String classname) {
            return classname + "$$ProxyObjectOutputStreamTest" + counter++;
        }
    }

    public interface DescribedGreeting extends Serializable {
        String describe(String recipient);
    }

    public static class GreetingTarget implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String name;

        public GreetingTarget(String name) {
            this.name = name;
        }

        public String name() {
            return name;
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
