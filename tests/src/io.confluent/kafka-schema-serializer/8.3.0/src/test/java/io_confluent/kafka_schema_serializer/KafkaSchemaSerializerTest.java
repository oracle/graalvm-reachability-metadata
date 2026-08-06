/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_confluent.kafka_schema_serializer;

import java.util.HashMap;
import java.util.Map;

import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import io.confluent.kafka.serializers.context.NullContextNameStrategy;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/// Exercises the {@code io.confluent.kafka.serializers} infrastructure backed by an
/// in-memory {@code mock://} Schema Registry, so the test needs no live Kafka or
/// Schema Registry service. The Avro round trip reaches {@code AbstractKafkaSchemaSerDe},
/// {@code AbstractKafkaSchemaSerDeConfig}, and the {@code schema.id} serializers and
/// deserializers, while the parameterized cases reflectively load every subject- and
/// context-name strategy exactly as the config does at runtime, which is what the
/// reachability metadata for this artifact must cover.
class KafkaSchemaSerializerTest {

    private static final String TOPIC = "test-topic";

    private static final String SCHEMA_DEFINITION = """
            {
              "type": "record",
              "name": "User",
              "namespace": "io.confluent.example",
              "fields": [
                {"name": "name", "type": "string"},
                {"name": "age", "type": "int"}
              ]
            }
            """;

    private static GenericRecord newUser() {
        Schema schema = new Schema.Parser().parse(SCHEMA_DEFINITION);
        GenericRecord record = new GenericData.Record(schema);
        record.put("name", "alice");
        record.put("age", 30);
        return record;
    }

    @Test
    void roundTripThroughMockSchemaRegistry() {
        Map<String, Object> config = new HashMap<>();
        config.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, "mock://round-trip");
        config.put(AbstractKafkaSchemaSerDeConfig.CONTEXT_NAME_STRATEGY, NullContextNameStrategy.class.getName());

        try (KafkaAvroSerializer serializer = new KafkaAvroSerializer();
                KafkaAvroDeserializer deserializer = new KafkaAvroDeserializer()) {
            serializer.configure(config, false);
            deserializer.configure(config, false);

            byte[] payload = serializer.serialize(TOPIC, newUser());
            assertThat(payload).isNotEmpty();

            Object deserialized = deserializer.deserialize(TOPIC, payload);
            assertThat(deserialized).isInstanceOf(GenericRecord.class);
            assertThat(((GenericRecord) deserialized).get("name")).hasToString("alice");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "io.confluent.kafka.serializers.subject.TopicNameStrategy",
            "io.confluent.kafka.serializers.subject.RecordNameStrategy",
            "io.confluent.kafka.serializers.subject.TopicRecordNameStrategy"})
    void serializesWithEachSubjectNameStrategy(String strategyClass) {
        Map<String, Object> config = new HashMap<>();
        config.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, "mock://subject-strategies");
        config.put(AbstractKafkaSchemaSerDeConfig.VALUE_SUBJECT_NAME_STRATEGY, strategyClass);

        try (KafkaAvroSerializer serializer = new KafkaAvroSerializer()) {
            serializer.configure(config, false);
            byte[] payload = serializer.serialize(TOPIC, newUser());
            assertThat(payload).isNotEmpty();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "io.confluent.kafka.serializers.subject.TopicNameStrategy",
            "io.confluent.kafka.serializers.subject.RecordNameStrategy",
            "io.confluent.kafka.serializers.subject.TopicRecordNameStrategy",
            "io.confluent.kafka.serializers.subject.DefaultReferenceSubjectNameStrategy",
            "io.confluent.kafka.serializers.subject.QualifiedReferenceSubjectNameStrategy",
            "io.confluent.kafka.serializers.subject.AssociatedNameStrategy",
            "io.confluent.kafka.serializers.context.NullContextNameStrategy"})
    void strategyTypesAreReflectivelyInstantiable(String strategyClass) throws Exception {
        // Confluent loads these strategies reflectively from configuration via their
        // public no-arg constructor; the same path must work under native image.
        Class<?> type = Class.forName(strategyClass);
        Object instance = type.getDeclaredConstructor().newInstance();
        assertThat(instance).isNotNull();
    }
}
