/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.internal.util.SerializationHelper;
import org.junit.jupiter.api.Test;

import java.io.Serializable;

import static org.assertj.core.api.Assertions.assertThat;

public class SerializationHelperInnerCustomObjectInputStreamTest {

    @Test
    public void resolvesApplicationClassesWhileDeserializing() {
        Payload original = new Payload("native");
        byte[] bytes = SerializationHelper.serialize(original);

        Payload copy = (Payload) SerializationHelper.deserialize(
                bytes,
                Payload.class.getClassLoader()
        );

        assertThat(copy.getValue()).isEqualTo("native");
    }

    public static class Payload implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String value;

        public Payload(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}
