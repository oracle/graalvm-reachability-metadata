/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.avro.reflect.AvroEncode;
import org.apache.pulsar.client.schema.AvroTrustedClasses;
import org.apache.pulsar.shade.org.apache.avro.Schema;
import org.apache.pulsar.shade.org.apache.avro.io.BinaryEncoder;
import org.apache.pulsar.shade.org.apache.avro.io.Decoder;
import org.apache.pulsar.shade.org.apache.avro.io.DecoderFactory;
import org.apache.pulsar.shade.org.apache.avro.io.Encoder;
import org.apache.pulsar.shade.org.apache.avro.io.EncoderFactory;
import org.apache.pulsar.shade.org.apache.avro.reflect.CustomEncoding;
import org.apache.pulsar.shade.org.apache.avro.reflect.ReflectData;
import org.apache.pulsar.shade.org.apache.avro.reflect.ReflectDatumReader;
import org.apache.pulsar.shade.org.apache.avro.reflect.ReflectDatumWriter;

public final class AvroReflectionRoundTripSupport {
    private AvroReflectionRoundTripSupport() {
    }

    public static void roundTrip() throws Exception {
        synchronized (AvroTrustedClasses.class) {
            AvroTrustedClasses.State previousTrust = AvroTrustedClasses.snapshot();
            AvroTrustedClasses.trustExactly(TopicRecord.class);
            try {
                ReflectData reflectData = new ReflectData();
                Schema schema = reflectData.getSchema(TopicRecord.class);
                TopicRecord input =
                        new TopicRecord("persistent", new String[] {"pulsar", "bookkeeper"});
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(bytes, null);
                new ReflectDatumWriter<TopicRecord>(schema, reflectData).write(input, encoder);
                encoder.flush();

                ReflectDatumReader<TopicRecord> reader =
                        new ReflectDatumReader<>(schema, schema, reflectData);
                TopicRecord restored = reader.read(
                        null, DecoderFactory.get().binaryDecoder(bytes.toByteArray(), null));

                assertThat(restored.name).isEqualTo("persistent");
                assertThat(restored.labels)
                        .isInstanceOf(String[].class)
                        .containsExactly("pulsar", "bookkeeper");
            } finally {
                AvroTrustedClasses.restore(previousTrust);
            }
        }
    }

    public static void roundTripCustomEncoding() throws Exception {
        synchronized (AvroTrustedClasses.class) {
            AvroTrustedClasses.State previousTrust = AvroTrustedClasses.snapshot();
            AvroTrustedClasses.trustExactly(CustomRecord.class, EncodedValue.class);
            try {
                ReflectData reflectData = new ReflectData();
                Schema schema = reflectData.getSchema(CustomRecord.class);
                CustomRecord input = new CustomRecord(new EncodedValue("persistent"));
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(bytes, null);
                new ReflectDatumWriter<CustomRecord>(schema, reflectData).write(input, encoder);
                encoder.flush();

                ReflectDatumReader<CustomRecord> reader =
                        new ReflectDatumReader<>(schema, schema, reflectData);
                CustomRecord restored = reader.read(
                        null, DecoderFactory.get().binaryDecoder(bytes.toByteArray(), null));

                assertThat(restored.value.text).isEqualTo("persistent");
            } finally {
                AvroTrustedClasses.restore(previousTrust);
            }
        }
    }

    public static class TopicRecord {
        public String name;
        public String[] labels;

        public TopicRecord() {
        }

        public TopicRecord(String name, String[] labels) {
            this.name = name;
            this.labels = labels;
        }
    }

    public static class CustomRecord {
        @AvroEncode(using = ValueEncoding.class)
        public EncodedValue value;

        public CustomRecord() {
        }

        public CustomRecord(EncodedValue value) {
            this.value = value;
        }
    }

    public static class EncodedValue {
        private final String text;

        public EncodedValue() {
            this("");
        }

        public EncodedValue(String text) {
            this.text = text;
        }
    }

    public static class ValueEncoding extends CustomEncoding<EncodedValue> {
        public ValueEncoding() {
            schema = Schema.create(Schema.Type.STRING);
        }

        @Override
        protected void write(Object datum, Encoder output) throws IOException {
            output.writeString(((EncodedValue) datum).text);
        }

        @Override
        protected EncodedValue read(Object reuse, Decoder input) throws IOException {
            return new EncodedValue(input.readString().toString());
        }
    }
}
