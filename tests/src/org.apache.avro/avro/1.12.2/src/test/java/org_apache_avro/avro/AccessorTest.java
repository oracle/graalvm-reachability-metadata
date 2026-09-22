/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_avro.avro;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.apache.avro.Schema;
import org.apache.avro.Schema.Field;
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.util.internal.Accessor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AccessorTest {
    @Test
    void lazilyInitializesInternalAccessorsForFieldCreationAndDefaultEncoding() throws IOException {
        Schema stringSchema = Schema.create(Schema.Type.STRING);
        TextNode defaultValue = TextNode.valueOf("default value");

        Field field = Accessor.createField("fieldName", stringSchema, "field documentation", defaultValue);

        assertThat(field.name()).isEqualTo("fieldName");
        assertThat(field.doc()).isEqualTo("field documentation");
        assertThat(field.schema()).isSameAs(stringSchema);
        assertThat(Accessor.defaultValue(field)).isEqualTo(defaultValue);

        Schema intSchema = Schema.create(Schema.Type.INT);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(output, null);

        Accessor.encode(encoder, intSchema, IntNode.valueOf(42));
        encoder.flush();

        BinaryDecoder decoder = DecoderFactory.get().binaryDecoder(output.toByteArray(), null);
        assertThat(decoder.readInt()).isEqualTo(42);
    }
}
