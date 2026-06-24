/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_avro.avro;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.Encoder;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.io.FastReaderBuilder;
import org.apache.avro.specific.SpecificData;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FastReaderBuilderTest {
    @Test
    void createsStringClassValuesFromSchemaClassProperty() throws IOException {
        Schema schema = Schema.create(Schema.Type.STRING);
        GenericData.setStringType(schema, GenericData.StringType.String);
        schema.addProp(SpecificData.CLASS_PROP, StringToken.class.getName());
        DatumReader<Object> reader = FastReaderBuilder.get().createDatumReader(schema);

        Object value = reader.read(null, DecoderFactory.get().binaryDecoder(encodeString("avro"), null));

        assertThat(value).isEqualTo(new StringToken("avro"));
    }

    private static byte[] encodeString(String value) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Encoder encoder = EncoderFactory.get().binaryEncoder(output, null);
        encoder.writeString(value);
        encoder.flush();
        return output.toByteArray();
    }

    public static final class StringToken {
        private final String value;

        public StringToken(String value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof StringToken)) {
                return false;
            }
            StringToken that = (StringToken) other;
            return value.equals(that.value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }
    }
}
