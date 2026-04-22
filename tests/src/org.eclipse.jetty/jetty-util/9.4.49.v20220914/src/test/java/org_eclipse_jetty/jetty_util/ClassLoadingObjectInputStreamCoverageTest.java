/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty.jetty_util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import org.eclipse.jetty.util.ClassLoadingObjectInputStream;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassLoadingObjectInputStreamCoverageTest {
    public static class SamplePayload implements Serializable {
        private final String value;

        public SamplePayload(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    public interface Echo extends Serializable {
        String echo(String value);
    }

    public static class EchoHandler implements InvocationHandler, Serializable {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if ("echo".equals(method.getName())) {
                return args[0];
            }
            if ("toString".equals(method.getName())) {
                return "echo-proxy";
            }
            return null;
        }
    }

    @Test
    void classLoadingObjectInputStreamDeserializesObjectsAndProxies() throws Exception {
        byte[] payloadBytes = serialize(new SamplePayload("jetty"));
        try (ClassLoadingObjectInputStream stream = new ClassLoadingObjectInputStream(new ByteArrayInputStream(payloadBytes))) {
            SamplePayload payload = (SamplePayload) stream.readObject(getClass().getClassLoader());
            assertThat(payload.getValue()).isEqualTo("jetty");
        }

        try (ClassLoadingObjectInputStream stream = new ClassLoadingObjectInputStream(new ByteArrayInputStream(payloadBytes))) {
            SamplePayload payload = (SamplePayload) stream.readObject(new ClassLoader(null) {
            });
            assertThat(payload.getValue()).isEqualTo("jetty");
        }

        Echo proxy = (Echo) Proxy.newProxyInstance(
            Echo.class.getClassLoader(),
            new Class<?>[]{Echo.class},
            new EchoHandler()
        );
        byte[] proxyBytes = serialize(proxy);
        try (ClassLoadingObjectInputStream stream = new ClassLoadingObjectInputStream(new ByteArrayInputStream(proxyBytes))) {
            Echo deserialized = (Echo) stream.readObject(getClass().getClassLoader());
            assertThat(deserialized.echo("value")).isEqualTo("value");
        }
    }

    private static byte[] serialize(Object value) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.writeObject(value);
        }
        return outputStream.toByteArray();
    }
}
