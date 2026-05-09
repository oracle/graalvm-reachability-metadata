/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_avro.avro;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.apache.avro.Schema;
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.reflect.ReflectData;
import org.apache.avro.reflect.ReflectDatumReader;
import org.apache.avro.reflect.ReflectDatumWriter;
import org.apache.avro.reflect.Stringable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ReflectDatumReaderTest {
    @Test
    void readRecreatesJavaArrayAndStringableValue() throws IOException {
        ReflectData reflectData = new ReflectData(ReflectDatumReaderTest.class.getClassLoader());
        Schema schema = reflectData.getSchema(ReaderRecord.class);
        ReaderRecord original = new ReaderRecord();
        original.tags = new String[] {"native", "image"};
        original.code = new StringValue("avro-174");

        byte[] encoded = encode(schema, reflectData, original);
        ReflectDatumReader<ReaderRecord> reader = new ReflectDatumReader<>(schema, schema, reflectData);
        BinaryDecoder decoder = DecoderFactory.get().binaryDecoder(new ByteArrayInputStream(encoded), null);

        ReaderRecord decoded = reader.read(null, decoder);

        assertArrayEquals(new String[] {"native", "image"}, decoded.tags);
        assertEquals(new StringValue("avro-174"), decoded.code);
    }

    private static byte[] encode(Schema schema, ReflectData reflectData, ReaderRecord record) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ReflectDatumWriter<ReaderRecord> writer = new ReflectDatumWriter<>(schema, reflectData);
        BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(output, null);

        writer.write(record, encoder);
        encoder.flush();

        return output.toByteArray();
    }

    public static final class ReaderRecord {
        public String[] tags;
        public StringValue code;

        public ReaderRecord() {
        }
    }

    @Stringable
    public static final class StringValue {
        private final String value;

        public StringValue(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return value;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof StringValue)) {
                return false;
            }
            StringValue that = (StringValue) other;
            return value.equals(that.value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }
    }
}
