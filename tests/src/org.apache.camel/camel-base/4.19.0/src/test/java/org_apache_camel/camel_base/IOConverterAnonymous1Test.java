/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_camel.camel_base;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.Serializable;

import org.apache.camel.converter.IOConverter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class IOConverterAnonymous1Test {
    @Test
    void toObjectInputResolvesSerializedClassWithJdkFallbackWhenExchangeIsAbsent() throws Exception {
        byte[] serializedPayload = serialize(new SerializablePayload("camel-io", 4));

        try (ObjectInput objectInput = IOConverter.toObjectInput(new ByteArrayInputStream(serializedPayload), null)) {
            Object deserialized = objectInput.readObject();

            assertThat(deserialized).isInstanceOf(SerializablePayload.class);
            SerializablePayload payload = (SerializablePayload) deserialized;
            assertThat(payload.message).isEqualTo("camel-io");
            assertThat(payload.value).isEqualTo(4);
        }
    }

    private byte[] serialize(SerializablePayload payload) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ObjectOutput objectOutput = IOConverter.toObjectOutput(outputStream)) {
            objectOutput.writeObject(payload);
        }
        return outputStream.toByteArray();
    }

    private static final class SerializablePayload implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String message;
        private final int value;

        private SerializablePayload(String message, int value) {
            this.message = message;
            this.value = value;
        }
    }
}
