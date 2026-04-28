/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_netty.netty;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jboss.netty.handler.codec.serialization.ObjectDecoderInputStream;
import org.jboss.netty.handler.codec.serialization.ObjectEncoderOutputStream;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CompactObjectInputStreamTest {
    @Test
    void usesProvidedClassLoaderForThinDescriptors() throws Exception {
        List<String> payload = new ArrayList<String>(Arrays.asList("alpha", "beta"));

        Object decoded = new ObjectDecoderInputStream(
                new ByteArrayInputStream(serialize(payload)),
                getClass().getClassLoader())
                .readObject();

        assertThat(decoded).isEqualTo(payload);
    }

    @Test
    void fallsBackToClassForNameWhenNoClassLoaderIsAvailable() throws Exception {
        List<String> payload = new ArrayList<String>(Arrays.asList("alpha", "beta"));
        Thread currentThread = Thread.currentThread();
        ClassLoader originalClassLoader = currentThread.getContextClassLoader();

        try {
            currentThread.setContextClassLoader(null);
            Object decoded = new ObjectDecoderInputStream(new ByteArrayInputStream(serialize(payload))).readObject();
            assertThat(decoded).isEqualTo(payload);
        } finally {
            currentThread.setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    void fallsBackToObjectInputStreamResolveClassForFatDescriptors() throws Exception {
        String[] payload = new String[] {"alpha", "beta"};

        Object decoded = new ObjectDecoderInputStream(
                new ByteArrayInputStream(serialize(payload)),
                new RejectingClassLoader())
                .readObject();

        assertThat((String[]) decoded).containsExactly(payload);
    }

    private static byte[] serialize(Object value) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ObjectEncoderOutputStream encoder = new ObjectEncoderOutputStream(outputStream);
        encoder.writeObject(value);
        encoder.close();
        return outputStream.toByteArray();
    }

    public static final class RejectingClassLoader extends ClassLoader {
        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            throw new ClassNotFoundException(name);
        }
    }
}
