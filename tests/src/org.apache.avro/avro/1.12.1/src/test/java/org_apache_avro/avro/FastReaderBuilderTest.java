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
import org.apache.avro.generic.GenericDatumWriter;
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
    void readsStringWithJavaClassPropertyUsingFastReader() throws IOException {
        Schema writerSchema = Schema.create(Schema.Type.STRING);
        Schema readerSchema = Schema.create(Schema.Type.STRING);
        readerSchema.addProp(GenericData.STRING_PROP, GenericData.StringType.String.name());
        readerSchema.addProp(SpecificData.CLASS_PROP, StringValue.class.getName());

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Encoder encoder = EncoderFactory.get().binaryEncoder(output, null);
        GenericDatumWriter<Object> writer = new GenericDatumWriter<>(writerSchema);
        writer.write("covered", encoder);
        encoder.flush();

        DatumReader<Object> reader = FastReaderBuilder.get().createDatumReader(writerSchema, readerSchema);
        Object value = reader.read(null, DecoderFactory.get().binaryDecoder(output.toByteArray(), null));

        assertThat(value).isInstanceOf(StringValue.class);
        assertThat(((StringValue) value).value()).isEqualTo("covered");
    }

    public static final class StringValue {
        private final String value;

        public StringValue(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }
}
