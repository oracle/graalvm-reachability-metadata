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

public class Base64Anonymous1Test {
    @Test
    void decodeToObjectFallsBackToDefaultClassResolutionWhenCustomLoaderReturnsNull() throws Exception {
        SerializableMessage message = new SerializableMessage("cluster-event", 19);
        String encodedObject = Base64.encodeObject(message);
        ClassLoader classLoader = new NullReturningClassLoader(SerializableMessage.class.getName());

        Object decodedObject = Base64.decodeToObject(encodedObject, Base64.NO_OPTIONS, classLoader);

        assertThat(decodedObject).isEqualTo(message);
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

    private static final class NullReturningClassLoader extends ClassLoader {
        private final String unresolvedClassName;

        private NullReturningClassLoader(String unresolvedClassName) {
            super(Base64Anonymous1Test.class.getClassLoader());
            this.unresolvedClassName = unresolvedClassName;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if(unresolvedClassName.equals(name)) {
                return null;
            }
            return super.loadClass(name, resolve);
        }
    }
}
