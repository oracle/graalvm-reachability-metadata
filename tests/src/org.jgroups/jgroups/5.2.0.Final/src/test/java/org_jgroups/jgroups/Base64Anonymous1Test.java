/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import org.jgroups.util.Base64;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class Base64Anonymous1Test {
    @Test
    void decodeToObjectResolvesSerializedClassThroughCustomLoader() throws Exception {
        SerializableMessage message = new SerializableMessage("cluster-event", 19);
        String encodedObject = Base64.encodeObject(message);
        RecordingClassLoader classLoader = new RecordingClassLoader(SerializableMessage.class.getName());

        Object decodedObject = Base64.decodeToObject(encodedObject, Base64.NO_OPTIONS, classLoader);

        assertThat(decodedObject).isEqualTo(message);
        assertThat(classLoader.resolvedExpectedClass()).isTrue();
    }

    @Test
    void decodeToObjectPropagatesClassNotFoundWhenCustomLoaderCannotResolveSerializedClass() throws Exception {
        SerializableMessage message = new SerializableMessage("missing-loader-event", 23);
        String encodedObject = Base64.encodeObject(message);
        FailingClassLoader classLoader = new FailingClassLoader(SerializableMessage.class.getName());

        assertThatExceptionOfType(ClassNotFoundException.class)
                .isThrownBy(() -> Base64.decodeToObject(encodedObject, Base64.NO_OPTIONS, classLoader));
        assertThat(classLoader.attemptedExpectedClass()).isTrue();
    }

    public static final class SerializableMessage implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String name;
        private final int sequence;

        public SerializableMessage(String name, int sequence) {
            this.name = name;
            this.sequence = sequence;
        }

        @Override
        public boolean equals(Object other) {
            if(this == other) {
                return true;
            }
            if(!(other instanceof SerializableMessage)) {
                return false;
            }
            SerializableMessage that = (SerializableMessage)other;
            return sequence == that.sequence && Objects.equals(name, that.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, sequence);
        }
    }

    private static final class RecordingClassLoader extends ClassLoader {
        private final String expectedClassName;
        private boolean resolvedExpectedClass;

        private RecordingClassLoader(String expectedClassName) {
            super(Base64Anonymous1Test.class.getClassLoader());
            this.expectedClassName = expectedClassName;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if(expectedClassName.equals(name)) {
                resolvedExpectedClass = true;
            }
            return super.loadClass(name, resolve);
        }

        private boolean resolvedExpectedClass() {
            return resolvedExpectedClass;
        }
    }

    private static final class FailingClassLoader extends ClassLoader {
        private final String expectedClassName;
        private final String expectedInternalClassName;
        private boolean attemptedExpectedClass;

        private FailingClassLoader(String expectedClassName) {
            super(Base64Anonymous1Test.class.getClassLoader());
            this.expectedClassName = expectedClassName;
            this.expectedInternalClassName = expectedClassName.replace('.', '/');
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if(expectedClassName.equals(name) || expectedInternalClassName.equals(name)) {
                attemptedExpectedClass = true;
                throw new ClassNotFoundException(name);
            }
            return super.loadClass(name, resolve);
        }

        private boolean attemptedExpectedClass() {
            return attemptedExpectedClass;
        }
    }
}
