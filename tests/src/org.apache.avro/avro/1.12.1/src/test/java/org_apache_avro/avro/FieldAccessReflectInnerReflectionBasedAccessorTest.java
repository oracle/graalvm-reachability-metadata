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
import org.apache.avro.reflect.ReflectData;
import org.apache.avro.reflect.ReflectDatumReader;
import org.apache.avro.reflect.ReflectDatumWriter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FieldAccessReflectInnerReflectionBasedAccessorTest {

    @Test
    void setsPrimitiveFieldToDefaultWhenDatumValueIsNull() {
        ReflectData reflectData = new ReflectData();
        PlainReflectRecord record = new PlainReflectRecord("unchanged", 42);

        reflectData.setField(record, "count", 1, null);

        assertThat(record.getMessage()).isEqualTo("unchanged");
        assertThat(record.getCount()).isZero();
    }

    @Test
    void decodesPlainReflectRecordThroughReflectionBasedAccessor() throws IOException {
        ReflectData reflectData = new ReflectData();
        Schema schema = reflectData.getSchema(PlainReflectRecord.class);
        PlainReflectRecord original = new PlainReflectRecord("written through reflection", 42);

        byte[] encoded = encodeRecord(reflectData, schema, original);
        PlainReflectRecord decoded = decodeRecord(reflectData, schema, encoded);

        assertThat(decoded.getMessage()).isEqualTo("written through reflection");
        assertThat(decoded.getCount()).isEqualTo(42);
    }

    private static byte[] encodeRecord(ReflectData reflectData, Schema schema, PlainReflectRecord record)
            throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Encoder encoder = EncoderFactory.get().binaryEncoder(output, null);
        ReflectDatumWriter<PlainReflectRecord> writer = new ReflectDatumWriter<>(schema, reflectData);

        writer.write(record, encoder);
        encoder.flush();
        return output.toByteArray();
    }

    private static PlainReflectRecord decodeRecord(ReflectData reflectData, Schema schema, byte[] encoded)
            throws IOException {
        Decoder decoder = DecoderFactory.get().binaryDecoder(encoded, null);
        ReflectDatumReader<PlainReflectRecord> reader = new ReflectDatumReader<>(schema, schema, reflectData);

        return reader.read(null, decoder);
    }

    public static class PlainReflectRecord {
        private String message;
        private int count;

        public PlainReflectRecord() {
        }

        public PlainReflectRecord(String message, int count) {
            this.message = message;
            this.count = count;
        }

        public String getMessage() {
            return message;
        }

        public int getCount() {
            return count;
        }
    }
}
