/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import org.apache.calcite.util.SerializableCharset;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class SerializableCharsetTest {
    @Test
    public void preservesCharsetAcrossJavaSerializationRoundTrip() throws Exception {
        SerializableCharset original = SerializableCharset.forCharset(StandardCharsets.UTF_8);

        SerializableCharset restored = deserialize(serialize(original));

        assertThat(restored).isNotSameAs(original);
        assertThat(restored.getCharset()).isEqualTo(StandardCharsets.UTF_8);
        assertThat(restored.getCharset().newEncoder().canEncode("Calcite π")).isTrue();
    }

    @Test
    public void restoresNamedCharsetAfterDeserialization() throws Exception {
        Charset sourceCharset = Charset.forName("ISO-8859-1");
        SerializableCharset original = SerializableCharset.forCharset(sourceCharset);

        SerializableCharset restored = deserialize(serialize(original));

        assertThat(restored.getCharset()).isEqualTo(sourceCharset);
        assertThat(restored.getCharset().name()).isEqualTo(sourceCharset.name());
    }

    private static byte[] serialize(SerializableCharset value) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutput = new ObjectOutputStream(output)) {
            objectOutput.writeObject(value);
        }
        return output.toByteArray();
    }

    private static SerializableCharset deserialize(byte[] serialized) throws IOException, ClassNotFoundException {
        try (ObjectInputStream objectInput = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            return (SerializableCharset) objectInput.readObject();
        }
    }
}
