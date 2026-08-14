/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_confluent.kafka_schema_serializer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.confluent.kafka.schemaregistry.ParsedSchema;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClientFactory;
import io.confluent.kafka.schemaregistry.client.rest.entities.Metadata;
import io.confluent.kafka.schemaregistry.client.rest.entities.RuleSet;
import io.confluent.kafka.schemaregistry.client.rest.entities.SchemaEntity;
import io.confluent.kafka.schemaregistry.client.rest.entities.SchemaReference;
import io.confluent.kafka.schemaregistry.testutil.MockSchemaRegistry;
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.serializers.context.NullContextNameStrategy;
import io.confluent.kafka.serializers.schema.id.ConfigSchemaIdDeserializer;
import io.confluent.kafka.serializers.schema.id.DualSchemaIdDeserializer;
import io.confluent.kafka.serializers.schema.id.HeaderSchemaIdSerializer;
import io.confluent.kafka.serializers.schema.id.PrefixSchemaIdDeserializer;
import io.confluent.kafka.serializers.schema.id.PrefixSchemaIdSerializer;
import io.confluent.kafka.serializers.schema.id.SchemaId;
import io.confluent.kafka.serializers.subject.AssociatedNameStrategy;
import io.confluent.kafka.serializers.subject.DefaultReferenceSubjectNameStrategy;
import io.confluent.kafka.serializers.subject.QualifiedReferenceSubjectNameStrategy;
import io.confluent.kafka.serializers.subject.RecordNameStrategy;
import io.confluent.kafka.serializers.subject.TopicNameStrategy;
import io.confluent.kafka.serializers.subject.TopicRecordNameStrategy;
import io.confluent.kafka.serializers.wrapper.WrapperKeyDeserializer;
import io.confluent.kafka.serializers.wrapper.WrapperKeyDeserializerConfig;
import io.confluent.kafka.serializers.wrapper.WrapperKeySerializer;
import io.confluent.kafka.serializers.wrapper.WrapperKeySerializerConfig;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;

public class KafkaSchemaSerializerTest {

    @Test
    void baseConfigCreatesDefaultStrategyImplementationsAndKeepsMockRegistryUrl() {
        String scope = "schema-serializer-config-" + UUID.randomUUID();
        Map<String, Object> props = new HashMap<>();
        props.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, "mock://" + scope);
        props.put(AbstractKafkaSchemaSerDeConfig.REQUEST_HEADER_PREFIX + "X-Test", "metadata");
        props.put(AbstractKafkaSchemaSerDeConfig.USE_SCHEMA_ID, 42);
        props.put(AbstractKafkaSchemaSerDeConfig.EXECUTION_ENVIRONMENT, "CLIENT");
        props.put(AbstractKafkaSchemaSerDeConfig.RULE_SERVICE_LOADER_ENABLE, false);

        try {
            AbstractKafkaSchemaSerDeConfig config = new AbstractKafkaSchemaSerDeConfig(
                    AbstractKafkaSchemaSerDeConfig.baseConfigDef(), props);

            assertThat(config.getSchemaRegistryUrls()).containsExactly("mock://" + scope);
            assertThat(config.requestHeaders()).containsEntry("X-Test", "metadata");
            assertThat(config.useSchemaId()).isEqualTo(42);
            assertThat(config.enableRuleServiceLoader()).isFalse();
            assertThat(config.getExecutionEnvironment().name()).isEqualTo("CLIENT");
            assertThat(config.contextNameStrategy()).isInstanceOf(NullContextNameStrategy.class);
            assertThat(config.keySubjectNameStrategy()).isInstanceOf(AssociatedNameStrategy.class);
            assertThat(config.valueSubjectNameStrategy())
                    .isInstanceOf(AssociatedNameStrategy.class);
            assertThat(config.keySchemaIdSerializer()).isInstanceOf(PrefixSchemaIdSerializer.class);
            assertThat(config.valueSchemaIdDeserializer())
                    .isInstanceOf(DualSchemaIdDeserializer.class);
            assertThat(config.keySubjectNameStrategy().subjectName("orders", true, null))
                    .isEqualTo("orders-key");
            assertThat(config.valueSubjectNameStrategy().subjectName("orders", false, null))
                    .isEqualTo("orders-value");
        } finally {
            MockSchemaRegistry.dropScope(scope);
        }
    }

    @Test
    void schemaRegistryFactoryUsesSharedInProcessMockScope() throws Exception {
        String scope = "schema-serializer-registry-" + UUID.randomUUID();
        try {
            SchemaRegistryClient firstClient = SchemaRegistryClientFactory.newClient(
                    "mock://" + scope, 10, Collections.emptyList(), Collections.emptyMap(),
                    Collections.emptyMap());
            SchemaRegistryClient secondClient = SchemaRegistryClientFactory.newClient(
                    List.of("mock://" + scope), 10, Collections.emptyList(), Collections.emptyMap(),
                    Collections.emptyMap());
            ParsedSchema schema = new SimpleParsedSchema("JSON", "com.example.Order", 1);

            int id = firstClient.register("orders-value", schema);

            assertThat(secondClient.getSchemaBySubjectAndId("orders-value", id)).isEqualTo(schema);
        } finally {
            MockSchemaRegistry.dropScope(scope);
        }
    }

    @Test
    void prefixSchemaIdSerializerAndDeserializerRoundTripIdAndProtobufMessageIndexes() {
        SchemaId outgoing = new SchemaId("PROTOBUF", 321, (UUID) null);
        outgoing.setMessageIndexes(List.of(0, 2, 5));
        byte[] payload = new byte[] {9, 8, 7};

        byte[] serialized = new PrefixSchemaIdSerializer()
                .serialize("orders", false, new RecordHeaders(), payload, outgoing);
        SchemaId incoming = new SchemaId("PROTOBUF");
        ByteBuffer remaining = new PrefixSchemaIdDeserializer()
                .deserialize("orders", false, new RecordHeaders(), serialized, incoming);

        byte[] remainingPayload = new byte[remaining.remaining()];
        remaining.get(remainingPayload);
        assertThat(incoming.getId()).isEqualTo(321);
        assertThat(incoming.getMessageIndexes()).containsExactly(0, 2, 5);
        assertThat(remainingPayload).containsExactly(payload);
    }

    @Test
    void headerSchemaIdSerializerStoresGuidOnceAndDualDeserializerPrefersHeaders() {
        UUID guid = UUID.fromString("00000000-0000-0000-0000-000000000123");
        SchemaId outgoing = new SchemaId("JSON", null, guid);
        RecordHeaders headers = new RecordHeaders();
        byte[] payload = new byte[] {1, 2, 3};
        HeaderSchemaIdSerializer serializer = new HeaderSchemaIdSerializer();

        byte[] firstPayload = serializer.serialize("customers", true, headers, payload, outgoing);
        byte[] secondPayload = serializer.serialize("customers", true, headers, payload, outgoing);
        SchemaId incoming = new SchemaId("JSON");
        ByteBuffer remaining = new DualSchemaIdDeserializer()
                .deserialize("customers", true, headers, secondPayload, incoming);

        assertThat(firstPayload).isSameAs(payload);
        assertThat(secondPayload).isSameAs(payload);
        assertThat(headers.headers(SchemaId.KEY_SCHEMA_ID_HEADER)).hasSize(1);
        Header header = headers.lastHeader(SchemaId.KEY_SCHEMA_ID_HEADER);
        assertThat(header.value()[0]).isEqualTo(SchemaId.MAGIC_BYTE_V1);
        assertThat(incoming.getGuid()).isEqualTo(guid);
        assertThat(bufferBytes(remaining)).containsExactly(payload);
    }

    @Test
    void configSchemaIdDeserializerAppliesConfiguredIdGuidAndIndexesWithoutChangingPayload() {
        UUID guid = UUID.fromString("00000000-0000-0000-0000-000000000456");
        ConfigSchemaIdDeserializer deserializer = new ConfigSchemaIdDeserializer();
        deserializer.configure(Map.of(
                ConfigSchemaIdDeserializer.USE_SCHEMA_ID, "77",
                ConfigSchemaIdDeserializer.USE_SCHEMA_GUID, guid.toString(),
                ConfigSchemaIdDeserializer.USE_MESSAGE_INDEXES, "0, 3, 8"));
        byte[] payload = new byte[] {6, 5, 4};
        SchemaId schemaId = new SchemaId("PROTOBUF");

        ByteBuffer remaining = deserializer.deserialize(
                "events", false, new RecordHeaders(), payload, schemaId);

        assertThat(schemaId.getId()).isEqualTo(77);
        assertThat(schemaId.getGuid()).isEqualTo(guid);
        assertThat(schemaId.getMessageIndexes()).containsExactly(0, 3, 8);
        assertThat(bufferBytes(remaining)).containsExactly(payload);
    }

    @Test
    void wrapperKeySerializerAndDeserializerDelegateToConfiguredKafkaImplementations() {
        WrapperKeySerializer<String> serializer = new WrapperKeySerializer<>();
        WrapperKeyDeserializer<String> deserializer = new WrapperKeyDeserializer<>();
        RecordHeaders headers = new RecordHeaders();
        headers.add("source", new byte[] {1});

        try {
            serializer.configure(Map.of(
                    WrapperKeySerializerConfig.WRAPPED_KEY_SERIALIZER,
                    StringSerializer.class), true);
            deserializer.configure(Map.of(
                    WrapperKeyDeserializerConfig.WRAPPED_KEY_DESERIALIZER,
                    StringDeserializer.class), true);

            byte[] serialized = serializer.serialize("customers", headers, "customer-42");
            String deserialized = deserializer.deserialize("customers", headers, serialized);

            assertThat(serialized).containsExactly("customer-42".getBytes(StandardCharsets.UTF_8));
            assertThat(deserialized).isEqualTo("customer-42");
            assertThat(headers.lastHeader("source").value()).containsExactly((byte) 1);
            assertThat(serializer.serialize("customers", headers, null)).isNull();
            assertThat(deserializer.deserialize("customers", headers, (byte[]) null)).isNull();
        } finally {
            serializer.close();
            deserializer.close();
        }
    }

    @Test
    void subjectAndReferenceStrategiesDeriveNamesFromTopicRecordAndReferencePath() {
        ParsedSchema schema = new SimpleParsedSchema("JSON", "com.example.Customer", null);

        assertThat(new TopicNameStrategy().subjectName("customers", true, schema))
                .isEqualTo("customers-key");
        assertThat(new TopicNameStrategy().subjectName("customers", false, schema))
                .isEqualTo("customers-value");
        assertThat(new RecordNameStrategy().subjectName("ignored", false, schema))
                .isEqualTo("com.example.Customer");
        assertThat(new TopicRecordNameStrategy().subjectName("customers", false, schema))
                .isEqualTo("customers-com.example.Customer");
        assertThat(new DefaultReferenceSubjectNameStrategy()
                .subjectName("google/type/date.proto", "customers", false, schema))
                .isEqualTo("google/type/date.proto");
        assertThat(new QualifiedReferenceSubjectNameStrategy()
                .subjectName("google/type/date.proto", "customers", false, schema))
                .isEqualTo("google.type.date");
        assertThatExceptionOfType(SerializationException.class)
                .isThrownBy(() -> new RecordNameStrategy().subjectName("customers", false,
                        new SimpleParsedSchema("JSON", null, null)))
                .withMessageContaining("message value must only be a record schema");
    }

    @Test
    void schemaIdConvertsGuidAndIdEncodingsAndRejectsMissingIdentifiers() {
        UUID guid = UUID.fromString("00000000-0000-0000-0000-000000000789");
        SchemaId idSchemaId = new SchemaId("JSON", 19, (UUID) null);
        SchemaId guidSchemaId = new SchemaId("JSON", null, guid);

        SchemaId parsedId = new SchemaId("JSON");
        parsedId.fromBytes(ByteBuffer.wrap(idSchemaId.idToBytes()));
        SchemaId parsedGuid = new SchemaId("JSON");
        parsedGuid.fromBytes(ByteBuffer.wrap(guidSchemaId.guidToBytes()));

        assertThat(parsedId.getId()).isEqualTo(19);
        assertThat(parsedGuid.getGuid()).isEqualTo(guid);
        assertThatExceptionOfType(SerializationException.class)
                .isThrownBy(() -> new SchemaId("JSON").idToBytes())
                .withMessageContaining("Schema ID is null");
        assertThatExceptionOfType(SerializationException.class)
                .isThrownBy(() -> new SchemaId("JSON").guidToBytes())
                .withMessageContaining("Schema GUID is null");
    }

    private static byte[] bufferBytes(ByteBuffer buffer) {
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return bytes;
    }

    private static final class SimpleParsedSchema implements ParsedSchema {
        private final String schemaType;
        private final String name;
        private final Integer version;

        private SimpleParsedSchema(String schemaType, String name, Integer version) {
            this.schemaType = schemaType;
            this.name = name;
            this.version = version;
        }

        @Override
        public String schemaType() {
            return schemaType;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String canonicalString() {
            return schemaType + ":" + name;
        }

        @Override
        public Integer version() {
            return version;
        }

        @Override
        public List<SchemaReference> references() {
            return Collections.emptyList();
        }

        @Override
        public Metadata metadata() {
            return null;
        }

        @Override
        public RuleSet ruleSet() {
            return null;
        }

        @Override
        public ParsedSchema copy() {
            return new SimpleParsedSchema(schemaType, name, version);
        }

        @Override
        public ParsedSchema copy(Integer version) {
            return new SimpleParsedSchema(schemaType, name, version);
        }

        @Override
        public ParsedSchema copy(Metadata metadata, RuleSet ruleSet) {
            return copy();
        }

        @Override
        public ParsedSchema copy(Map<SchemaEntity, Set<String>> tagsToAdd,
                Map<SchemaEntity, Set<String>> tagsToRemove) {
            return copy();
        }

        @Override
        public List<String> isBackwardCompatible(ParsedSchema previousSchema) {
            return Collections.emptyList();
        }

        @Override
        public Object rawSchema() {
            return canonicalString();
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof SimpleParsedSchema)) {
                return false;
            }
            SimpleParsedSchema that = (SimpleParsedSchema) other;
            return Objects.equals(schemaType, that.schemaType)
                    && Objects.equals(name, that.name)
                    && Objects.equals(version, that.version);
        }

        @Override
        public int hashCode() {
            return Objects.hash(schemaType, name, version);
        }
    }
}
