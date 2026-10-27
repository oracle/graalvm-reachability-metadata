/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import org.apache.pulsar.client.schema.AvroTrustedClasses;
import org.apache.pulsar.shade.org.apache.avro.Schema;
import org.apache.pulsar.shade.org.apache.avro.io.BinaryEncoder;
import org.apache.pulsar.shade.org.apache.avro.io.DecoderFactory;
import org.apache.pulsar.shade.org.apache.avro.io.EncoderFactory;
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
}
