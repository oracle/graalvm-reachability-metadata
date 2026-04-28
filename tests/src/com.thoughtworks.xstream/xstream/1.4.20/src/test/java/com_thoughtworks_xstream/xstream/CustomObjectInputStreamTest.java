/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thoughtworks_xstream.xstream;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.NotActiveException;
import java.io.ObjectInputValidation;
import java.io.ObjectStreamClass;
import java.util.Map;

import com.thoughtworks.xstream.core.ClassLoaderReference;
import com.thoughtworks.xstream.core.util.CustomObjectInputStream;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CustomObjectInputStreamTest {
    @Test
    void readUnsharedDelegatesToCallbackReadObjectOverride() throws Exception {
        RecordingStreamCallback callback = new RecordingStreamCallback("payload");
        ExposingCustomObjectInputStream input = new ExposingCustomObjectInputStream(callback, null);

        Object result = input.readUnshared();

        assertThat(result).isEqualTo("payload");
        assertThat(callback.getReadCount()).isEqualTo(1);
    }

    @Test
    void resolveClassUsesObjectInputStreamWhenNoClassLoaderIsConfigured() throws Exception {
        ExposingCustomObjectInputStream input = new ExposingCustomObjectInputStream(
                new RecordingStreamCallback(null),
                null);

        Class<?> resolved = input.resolve(ObjectStreamClass.lookup(String.class));

        assertThat(resolved).isSameAs(String.class);
    }

    @Test
    void resolveClassUsesConfiguredClassLoaderReference() throws Exception {
        ExposingCustomObjectInputStream input = new ExposingCustomObjectInputStream(
                new RecordingStreamCallback(null),
                CustomObjectInputStreamTest.class.getClassLoader());

        Class<?> resolved = input.resolve(ObjectStreamClass.lookup(String.class));

        assertThat(resolved).isSameAs(String.class);
    }

    private static final class ExposingCustomObjectInputStream extends CustomObjectInputStream {
        ExposingCustomObjectInputStream(StreamCallback callback, ClassLoader classLoader) throws IOException {
            super(callback, new ClassLoaderReference(classLoader));
        }

        Class<?> resolve(ObjectStreamClass descriptor) throws IOException, ClassNotFoundException {
            return resolveClass(descriptor);
        }
    }

    private static final class RecordingStreamCallback implements CustomObjectInputStream.StreamCallback {
        private final Object object;
        private int readCount;

        RecordingStreamCallback(Object object) {
            this.object = object;
        }

        int getReadCount() {
            return readCount;
        }

        @Override
        public Object readFromStream() {
            readCount++;
            return object;
        }

        @Override
        public Map readFieldsFromStream() throws IOException {
            throw new IOException("No fields are available for this test stream");
        }

        @Override
        public void defaultReadObject() throws IOException {
            throw new IOException("Default object state is not available for this test stream");
        }

        @Override
        public void registerValidation(ObjectInputValidation validation, int priority)
                throws NotActiveException, InvalidObjectException {
            throw new NotActiveException("Validation callbacks are not active for this test stream");
        }

        @Override
        public void close() {
        }
    }
}
