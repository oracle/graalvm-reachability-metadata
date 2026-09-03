/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tools_jackson_dataformat.jackson_dataformat_avro;

import java.io.IOException;

import org.apache.avro.Schema;
import org.apache.avro.io.Decoder;
import org.apache.avro.io.Encoder;
import org.apache.avro.reflect.AvroEncode;
import org.apache.avro.reflect.CustomEncoding;
import org.junit.jupiter.api.Test;

import tools.jackson.dataformat.avro.AvroMapper;
import tools.jackson.dataformat.avro.AvroSchema;

import static org.assertj.core.api.Assertions.assertThat;

public class CustomEncodingWrapperTest {
    @Test
    void roundTripsSchemaBoundRecordWithCustomEncoding() throws Exception {
        AvroMapper mapper = new AvroMapper();
        AvroSchema schema = mapper.schemaFor(EncodedRecord.class);
        EncodedRecord original = new EncodedRecord(new EncodedValue("custom payload"));

        byte[] encoded = mapper.writer(schema).writeValueAsBytes(original);
        EncodedRecord decoded = mapper.readerFor(EncodedRecord.class).with(schema).readValue(encoded);

        assertThat(schema.getAvroSchema().getField("payload").schema().getType())
                .isEqualTo(Schema.Type.STRING);
        assertThat(decoded.payload.value()).isEqualTo("custom payload");
    }

    public static class EncodedRecord {
        @AvroEncode(using = ReversingEncoding.class)
        public EncodedValue payload;

        public EncodedRecord() {
        }

        public EncodedRecord(EncodedValue payload) {
            this.payload = payload;
        }
    }

    public static class EncodedValue {
        private final String value;

        public EncodedValue(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    public static class ReversingEncoding extends CustomEncoding<EncodedValue> {
        public ReversingEncoding() {
            schema = Schema.create(Schema.Type.STRING);
        }

        @Override
        protected void write(Object datum, Encoder out) throws IOException {
            EncodedValue value = (EncodedValue) datum;
            out.writeString(reverse(value.value()));
        }

        @Override
        protected EncodedValue read(Object reuse, Decoder in) throws IOException {
            return new EncodedValue(reverse(in.readString().toString()));
        }

        private static String reverse(String value) {
            return new StringBuilder(value).reverse().toString();
        }
    }
}
