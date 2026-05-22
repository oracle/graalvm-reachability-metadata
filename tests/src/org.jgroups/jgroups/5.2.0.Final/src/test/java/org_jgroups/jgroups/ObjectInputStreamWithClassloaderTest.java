/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import java.io.Serializable;
import java.net.URL;
import java.net.URLClassLoader;

import org.jgroups.util.Util;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ObjectInputStreamWithClassloaderTest {
    @Test
    void fallsBackToDefaultObjectInputStreamResolutionWhenCustomLoaderCannotLoadClass() throws Exception {
        SerializedValue expected = new SerializedValue("fallback", 520);
        byte[] bytes = Util.objectToByteBuffer(expected);

        try (URLClassLoader bootstrapOnlyLoader = new URLClassLoader(new URL[0], null)) {
            SerializedValue actual = Util.objectFromByteBuffer(bytes, 0, bytes.length, bootstrapOnlyLoader);

            assertThat(actual).isEqualTo(expected);
        }
    }

    public static class SerializedValue implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String name;
        private final int value;

        public SerializedValue(String newName, int newValue) {
            name = newName;
            value = newValue;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof SerializedValue)) {
                return false;
            }
            SerializedValue that = (SerializedValue) other;
            return value == that.value && name.equals(that.name);
        }

        @Override
        public int hashCode() {
            int result = name.hashCode();
            result = 31 * result + value;
            return result;
        }
    }
}
