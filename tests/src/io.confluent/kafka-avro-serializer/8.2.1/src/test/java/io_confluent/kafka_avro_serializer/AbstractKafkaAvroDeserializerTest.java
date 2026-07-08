/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_confluent.kafka_avro_serializer;

import static org.assertj.core.api.Assertions.assertThat;

import io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import java.util.HashMap;
import java.util.Map;
import org.apache.avro.Schema;
import org.apache.avro.specific.SpecificRecord;
import org.apache.avro.specific.SpecificRecordBase;
import org.junit.jupiter.api.Test;

public class AbstractKafkaAvroDeserializerTest {
    private static final String TOPIC = "specific-users";

    @Test
    void configuresSpecificReaderSchemaFromConfiguredValueType() {
        SchemaRegistryClient client = new MockSchemaRegistryClient();
        Map<String, Object> props = specificReaderProps();
        props.put(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_VALUE_TYPE_CONFIG, SpecificUser.class);

        KafkaAvroDeserializer deserializer = new KafkaAvroDeserializer(client, props, false);

        try {
            assertThat(deserializer.deserialize(TOPIC, null)).isNull();
        } finally {
            deserializer.close();
        }
    }

    @Test
    void deserializesSpecificRecordByResolvingReaderClassFromWriterSchema() {
        SchemaRegistryClient client = new MockSchemaRegistryClient();
        KafkaAvroSerializer serializer = new KafkaAvroSerializer(client, baseProps());
        KafkaAvroDeserializer deserializer = new KafkaAvroDeserializer(client, specificReaderProps(), false);

        try {
            byte[] payload = serializer.serialize(TOPIC, new SpecificUser("Ada", 37));

            Object value = deserializer.deserialize(TOPIC, payload);

            assertThat(value).isInstanceOf(SpecificUser.class);
            SpecificUser user = (SpecificUser) value;
            assertThat(user.getName().toString()).isEqualTo("Ada");
            assertThat(user.getAge()).isEqualTo(37);
        } finally {
            serializer.close();
            deserializer.close();
        }
    }

    private static Map<String, Object> specificReaderProps() {
        Map<String, Object> props = baseProps();
        props.put(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, true);
        return props;
    }

    private static Map<String, Object> baseProps() {
        Map<String, Object> props = new HashMap<>();
        props.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, "mock://unused");
        return props;
    }

    public static class SpecificUser extends SpecificRecordBase implements SpecificRecord {
        public static final Schema SCHEMA$ = new Schema.Parser().parse("""
                {
                  "type": "record",
                  "name": "SpecificUser",
                  "namespace": "io_confluent.kafka_avro_serializer.AbstractKafkaAvroDeserializerTest",
                  "fields": [
                    {"name": "name", "type": "string"},
                    {"name": "age", "type": "int"}
                  ]
                }
                """);

        private CharSequence name;
        private int age;

        public SpecificUser() {
        }

        public SpecificUser(String name, int age) {
            this.name = name;
            this.age = age;
        }

        @Override
        public Schema getSchema() {
            return SCHEMA$;
        }

        @Override
        public Object get(int field) {
            return switch (field) {
                case 0 -> name;
                case 1 -> age;
                default -> throw new IndexOutOfBoundsException("Unknown field index: " + field);
            };
        }

        @Override
        public void put(int field, Object value) {
            switch (field) {
                case 0 -> name = (CharSequence) value;
                case 1 -> age = (Integer) value;
                default -> throw new IndexOutOfBoundsException("Unknown field index: " + field);
            }
        }

        String getName() {
            return name.toString();
        }

        int getAge() {
            return age;
        }
    }
}
