/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.apache.calcite.util.SerializableCharset;
import org.junit.jupiter.api.Test;

public class SerializableCharsetTest {
    @Test
    void serializesCharsetByCanonicalName() throws Exception {
        Charset charset = StandardCharsets.UTF_16BE;
        SerializableCharset serializableCharset = SerializableCharset.forCharset(charset);

        byte[] serialized = serialize(serializableCharset);
        SerializableCharset deserialized = deserialize(serialized);

        assertThat(deserialized).isNotSameAs(serializableCharset);
        assertThat(deserialized.getCharset()).isEqualTo(charset);
        assertThat(deserialized.getCharset().name()).isEqualTo(charset.name());
    }

    private static byte[] serialize(SerializableCharset serializableCharset) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(serializableCharset);
        }
        return bytes.toByteArray();
    }

    private static SerializableCharset deserialize(byte[] serialized) throws Exception {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            return (SerializableCharset) input.readObject();
        }
    }
}
