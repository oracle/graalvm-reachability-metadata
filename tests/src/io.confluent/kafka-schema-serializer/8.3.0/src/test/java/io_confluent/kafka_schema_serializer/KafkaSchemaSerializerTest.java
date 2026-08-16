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
import io.confluent.kafka.serializers.KafkaAvroSerializerConfig;
import io.confluent.kafka.serializers.context.NullContextNameStrategy;
import io.confluent.kafka.serializers.context.strategy.ContextNameStrategy;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializerConfig;
import io.confluent.kafka.serializers.subject.strategy.ReferenceSubjectNameStrategy;
import io.confluent.kafka.serializers.subject.strategy.SubjectNameStrategy;
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
/// deserializers. Every subject-, reference-subject- and context-name strategy is loaded
/// the only way Confluent loads them, by naming the class in configuration and letting
/// the serde config instantiate it reflectively, which is what the conditional
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

    private static Map<String, Object> configWithRegistryScope(String scope) {
        Map<String, Object> config = new HashMap<>();
        config.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, "mock://" + scope);
        return config;
    }

    @Test
    void roundTripThroughMockSchemaRegistry() {
        Map<String, Object> config = configWithRegistryScope("round-trip");
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
        Map<String, Object> config = configWithRegistryScope("subject-strategies");
        config.put(AbstractKafkaSchemaSerDeConfig.VALUE_SUBJECT_NAME_STRATEGY, strategyClass);

        try (KafkaAvroSerializer serializer = new KafkaAvroSerializer()) {
            serializer.configure(config, false);
            byte[] payload = serializer.serialize(TOPIC, newUser());
            assertThat(payload).isNotEmpty();
        }
    }

    /// {@code AssociatedNameStrategy} needs a Schema Registry that serves associated
    /// subjects, so it is covered here through the same configuration-driven load the
    /// serializers perform instead of through a serialization round trip.
    @ParameterizedTest
    @ValueSource(strings = {
            "io.confluent.kafka.serializers.subject.TopicNameStrategy",
            "io.confluent.kafka.serializers.subject.RecordNameStrategy",
            "io.confluent.kafka.serializers.subject.TopicRecordNameStrategy",
            "io.confluent.kafka.serializers.subject.AssociatedNameStrategy"})
    void configurationLoadsSubjectNameStrategy(String strategyClass) {
        Map<String, Object> config = configWithRegistryScope("subject-strategy-config");
        config.put(AbstractKafkaSchemaSerDeConfig.KEY_SUBJECT_NAME_STRATEGY, strategyClass);
        config.put(AbstractKafkaSchemaSerDeConfig.VALUE_SUBJECT_NAME_STRATEGY, strategyClass);

        KafkaAvroSerializerConfig serdeConfig = new KafkaAvroSerializerConfig(config);
        SubjectNameStrategy keyStrategy = serdeConfig.keySubjectNameStrategy();
        SubjectNameStrategy valueStrategy = serdeConfig.valueSubjectNameStrategy();

        assertThat(keyStrategy.getClass().getName()).isEqualTo(strategyClass);
        assertThat(valueStrategy.getClass().getName()).isEqualTo(strategyClass);
    }

    @Test
    void configurationLoadsContextNameStrategy() {
        String strategyClass = NullContextNameStrategy.class.getName();
        Map<String, Object> config = configWithRegistryScope("context-strategy-config");
        config.put(AbstractKafkaSchemaSerDeConfig.CONTEXT_NAME_STRATEGY, strategyClass);

        ContextNameStrategy strategy = new KafkaAvroSerializerConfig(config).contextNameStrategy();

        assertThat(strategy.getClass().getName()).isEqualTo(strategyClass);
        assertThat(strategy.contextName(TOPIC)).isNull();
    }

    /// The reference-subject-name strategies are configured by the serdes that resolve
    /// schema references, so {@code KafkaProtobufSerializerConfig} is the public entry
    /// point that loads them; it extends {@code AbstractKafkaSchemaSerDeConfig} like the
    /// Avro config does.
    @ParameterizedTest
    @ValueSource(strings = {
            "io.confluent.kafka.serializers.subject.DefaultReferenceSubjectNameStrategy",
            "io.confluent.kafka.serializers.subject.QualifiedReferenceSubjectNameStrategy"})
    void configurationLoadsReferenceSubjectNameStrategy(String strategyClass) {
        Map<String, Object> config = configWithRegistryScope("reference-strategy-config");
        config.put(KafkaProtobufSerializerConfig.REFERENCE_SUBJECT_NAME_STRATEGY_CONFIG, strategyClass);

        ReferenceSubjectNameStrategy strategy =
                new KafkaProtobufSerializerConfig(config).referenceSubjectNameStrategyInstance();

        assertThat(strategy.getClass().getName()).isEqualTo(strategyClass);
    }
}
