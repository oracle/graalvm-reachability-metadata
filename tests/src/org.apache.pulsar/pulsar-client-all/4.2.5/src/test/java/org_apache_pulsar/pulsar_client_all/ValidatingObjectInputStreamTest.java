/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import org.apache.pulsar.shade.com.google.common.reflect.Reflection;
import org.apache.pulsar.shade.org.apache.commons.io.serialization.ValidatingObjectInputStream;
import org.apache.pulsar.shade.org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.api.Test;

public class ValidatingObjectInputStreamTest {
    @Test
    void readsAcceptedObjectWithGenericCast() throws Exception {
        byte[] encoded = SerializationUtils.serialize(new ArrayList<>(List.of("alpha", "beta")));

        try (ValidatingObjectInputStream input = new ValidatingObjectInputStream(
                new ByteArrayInputStream(encoded)).accept(ArrayList.class)) {
            List<String> restored = input.readObjectCast();

            assertThat(restored).containsExactly("alpha", "beta");
        }
    }

    @Test
    void validatesAndResolvesAcceptedProxyInterface() throws Exception {
        Greeting proxy = Reflection.newProxy(Greeting.class, new GreetingHandler());
        byte[] encoded = SerializationUtils.serialize(proxy);

        try (ValidatingObjectInputStream input = new ValidatingObjectInputStream(
                new ByteArrayInputStream(encoded)).accept(
                        Greeting.class, GreetingHandler.class, Proxy.class)) {
            Greeting restored = input.readObjectCast();

            assertThat(restored.greet("Pulsar")).isEqualTo("hello Pulsar");
        }
    }

    public interface Greeting extends Serializable {
        String greet(String name);
    }

    public static class GreetingHandler implements InvocationHandler, Serializable {
        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) {
            return "hello " + arguments[0];
        }
    }
}
