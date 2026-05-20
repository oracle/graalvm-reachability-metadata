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
import org.apache.avro.io.Decoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.Encoder;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.reflect.AvroEncode;
import org.apache.avro.reflect.CustomEncoding;
import org.apache.avro.reflect.ReflectData;
import org.apache.avro.reflect.ReflectDatumReader;
import org.apache.avro.reflect.ReflectDatumWriter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FieldAccessReflectTest {
    @Test
    void createsReflectiveFieldAccessorForCustomEncodedField() throws IOException {
        ReflectData reflectData = new ReflectData();
        Schema schema = reflectData.getSchema(CustomEncodedRecord.class);
        CustomEncodedRecord record = new CustomEncodedRecord(new EncodedValue("created through field accessor"));

        byte[] encoded = encodeRecord(reflectData, schema, record);
        CustomEncodedRecord decoded = decodeRecord(reflectData, schema, encoded);

        assertThat(decoded.encoded.value).isEqualTo("created through field accessor");
    }

    private static byte[] encodeRecord(ReflectData reflectData, Schema schema, CustomEncodedRecord record)
            throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Encoder encoder = EncoderFactory.get().binaryEncoder(output, null);
        ReflectDatumWriter<CustomEncodedRecord> writer = new ReflectDatumWriter<>(schema, reflectData);

        writer.write(record, encoder);
        encoder.flush();
        return output.toByteArray();
    }

    private static CustomEncodedRecord decodeRecord(ReflectData reflectData, Schema schema, byte[] encoded)
            throws IOException {
        Decoder decoder = DecoderFactory.get().binaryDecoder(encoded, null);
        ReflectDatumReader<CustomEncodedRecord> reader = new ReflectDatumReader<>(schema, schema, reflectData);

        return reader.read(null, decoder);
    }

    public static class CustomEncodedRecord {
        @AvroEncode(using = EncodedValueEncoding.class)
        public EncodedValue encoded;

        public CustomEncodedRecord() {
        }

        public CustomEncodedRecord(EncodedValue encoded) {
            this.encoded = encoded;
        }
    }

    public static class EncodedValue {
        private final String value;

        public EncodedValue(String value) {
            this.value = value;
        }
    }

    public static class EncodedValueEncoding extends CustomEncoding<EncodedValue> {
        public EncodedValueEncoding() {
            this.schema = Schema.create(Schema.Type.STRING);
        }

        @Override
        protected void write(Object datum, Encoder out) throws IOException {
            EncodedValue value = (EncodedValue) datum;
            out.writeString(value.value);
        }

        @Override
        protected EncodedValue read(Object reuse, Decoder in) throws IOException {
            return new EncodedValue(in.readString().toString());
        }
    }
}
