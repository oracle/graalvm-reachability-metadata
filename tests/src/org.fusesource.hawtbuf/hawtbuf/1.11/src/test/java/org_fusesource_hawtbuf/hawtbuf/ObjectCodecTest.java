/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_fusesource_hawtbuf.hawtbuf;

import org.fusesource.hawtbuf.DataByteArrayInputStream;
import org.fusesource.hawtbuf.DataByteArrayOutputStream;
import org.fusesource.hawtbuf.codec.ObjectCodec;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class ObjectCodecTest {

    @Test
    void encodesAndDecodesSerializablePayload() throws Exception {
        ObjectCodec<SerializablePayload> codec = new ObjectCodec<>();
        SerializablePayload payload = new SerializablePayload("hawtbuf", new ArrayList<>(List.of(1, 2, 3)));
        DataByteArrayOutputStream outputStream = new DataByteArrayOutputStream();

        codec.encode(payload, outputStream);

        DataByteArrayInputStream inputStream = new DataByteArrayInputStream(outputStream.toBuffer());
        SerializablePayload decoded = codec.decode(inputStream);

        assertThat(decoded).isEqualTo(payload);
    }

    private static final class SerializablePayload implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String name;
        private final List<Integer> values;

        private SerializablePayload(String name, List<Integer> values) {
            this.name = name;
            this.values = values;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof SerializablePayload that)) {
                return false;
            }
            return Objects.equals(name, that.name) && Objects.equals(values, that.values);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, values);
        }
    }
}
