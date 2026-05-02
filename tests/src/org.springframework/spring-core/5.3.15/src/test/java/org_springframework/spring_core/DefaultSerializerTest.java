/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.serializer.DefaultSerializer;

public class DefaultSerializerTest {

    @Test
    void serializesSerializableObjectToOutputStream() throws Exception {
        Map<String, Integer> payload = new LinkedHashMap<>();
        payload.put("spring", 5);
        payload.put("core", 3);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        new DefaultSerializer().serialize(payload, outputStream);

        assertThat(outputStream.toByteArray()).isNotEmpty();
        try (ObjectInputStream inputStream = new ObjectInputStream(new ByteArrayInputStream(outputStream.toByteArray()))) {
            assertThat(inputStream.readObject()).isEqualTo(payload);
        }
    }

    @Test
    void rejectsNonSerializableObjectBeforeWritingToStream() {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new DefaultSerializer().serialize(new Object(), outputStream))
                .withMessageContaining("requires a Serializable payload")
                .withMessageContaining(Object.class.getName());
        assertThat(outputStream.toByteArray()).isEmpty();
    }
}
