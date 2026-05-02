/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.io.EOFException;
import java.io.NotSerializableException;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.springframework.util.SerializationUtils;

public class SerializationUtilsTest {

    @Test
    void serializesAndDeserializesStringPayload() {
        String original = "spring-core serialization payload";

        byte[] serialized = SerializationUtils.serialize(original);
        Object deserialized = SerializationUtils.deserialize(serialized);

        assertThat(serialized).isNotEmpty();
        assertThat(deserialized).isEqualTo(original);
    }

    @Test
    void returnsNullForNullInput() {
        assertThat(SerializationUtils.serialize(null)).isNull();
        assertThat(SerializationUtils.deserialize(null)).isNull();
    }

    @Test
    void wrapsSerializationFailureForNonSerializableInput() {
        Object nonSerializable = new Object();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> SerializationUtils.serialize(nonSerializable))
                .withMessageContaining("Failed to serialize object of type")
                .withCauseInstanceOf(NotSerializableException.class);
    }

    @Test
    void wrapsDeserializationFailureForTruncatedPayload() {
        byte[] serialized = SerializationUtils.serialize("spring-core serialization payload");
        byte[] truncatedPayload = Arrays.copyOf(serialized, 4);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> SerializationUtils.deserialize(truncatedPayload))
                .withMessage("Failed to deserialize object")
                .withCauseInstanceOf(EOFException.class);
    }
}
