/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_dataformat.jackson_dataformat_yaml;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Jackson_dataformat_yamlTest {

    @Test
    void readTreeParsesNestedYamlDocumentsAndScalarTypes() throws Exception {
        YAMLMapper mapper = new YAMLMapper();

        String yaml = """
                # Application configuration expressed with common YAML structures.
                service:
                  name: billing-api
                  active: true
                  retries: 3
                  timeout: 2.5
                  endpoints:
                    - /health
                    - /metrics
                  headers: {Accept: application/json, Cache-Control: no-cache}
                description: |
                  first line
                  second line
                folded: >
                  alpha
                  beta
                emptyValue: null
                """;

        JsonNode root = mapper.readTree(yaml);
        JsonNode service = root.path("service");

        assertThat(service.path("name").asText()).isEqualTo("billing-api");
        assertThat(service.path("active").asBoolean()).isTrue();
        assertThat(service.path("retries").asInt()).isEqualTo(3);
        assertThat(service.path("timeout").asDouble()).isEqualTo(2.5d);
        assertThat(service.path("endpoints")).hasSize(2);
        assertThat(service.path("endpoints").get(0).asText()).isEqualTo("/health");
        assertThat(service.path("endpoints").get(1).asText()).isEqualTo("/metrics");
        assertThat(service.path("headers").path("Accept").asText()).isEqualTo("application/json");
        assertThat(service.path("headers").path("Cache-Control").asText()).isEqualTo("no-cache");
        assertThat(root.path("description").asText()).isEqualTo("first line\nsecond line\n");
        assertThat(root.path("folded").asText()).isEqualTo("alpha beta\n");
        assertThat(root.path("emptyValue").isNull()).isTrue();
    }

    @Test
    void writeValueRoundTripsOrderedMapsListsAndScalars() throws Exception {
        YAMLMapper mapper = YAMLMapper.builder()
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                .build();

        Map<String, Object> limits = new LinkedHashMap<>();
        limits.put("cpu", "500m");
        limits.put("memory", "256Mi");

        Map<String, Object> container = new LinkedHashMap<>();
        container.put("image", "example/app:1.0");
        container.put("ports", List.of(8080, 8443));
        container.put("limits", limits);

        Map<String, Object> document = new LinkedHashMap<>();
        document.put("name", "demo");
        document.put("enabled", true);
        document.put("replicas", 2);
        document.put("ratio", 0.75d);
        document.put("container", container);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        mapper.writeValue(output, document);

        byte[] serialized = output.toByteArray();
        String yaml = output.toString(StandardCharsets.UTF_8);
        JsonNode tree = mapper.readTree(serialized);
        Map<String, Object> roundTripped = mapper.readValue(
                serialized, new TypeReference<LinkedHashMap<String, Object>>() { });

        assertThat(yaml).doesNotStartWith("---");
        assertThat(yaml)
                .contains("name: \"demo\"")
                .contains("enabled: true")
                .contains("replicas: 2")
                .contains("container:")
                .contains("ports:")
                .contains("- 8080")
                .contains("cpu: \"500m\"");
        assertThat(tree.path("container").path("limits").path("memory").asText()).isEqualTo("256Mi");
        assertThat(tree.path("container").path("ports").get(1).asInt()).isEqualTo(8443);
        assertThat(roundTripped).containsEntry("name", "demo");
        assertThat(roundTripped).containsEntry("enabled", true);
        assertThat(((Number) roundTripped.get("replicas")).intValue()).isEqualTo(2);
        assertThat(((Number) roundTripped.get("ratio")).doubleValue()).isEqualTo(0.75d);
    }

    @Test
    void streamingParserExposesExpectedJsonTokenSequenceAndValues() throws Exception {
        YAMLFactory factory = new YAMLFactory();
        String yaml = """
                name: telemetry
                active: true
                threshold: 0.75
                retries: 4
                labels:
                  - core
                  - native
                nothing: null
                """;

        try (JsonParser parser = factory.createParser(yaml)) {
            assertThat(parser.nextToken()).isEqualTo(JsonToken.START_OBJECT);

            assertThat(parser.nextToken()).isEqualTo(JsonToken.FIELD_NAME);
            assertThat(parser.currentName()).isEqualTo("name");
            assertThat(parser.nextToken()).isEqualTo(JsonToken.VALUE_STRING);
            assertThat(parser.getText()).isEqualTo("telemetry");

            assertThat(parser.nextToken()).isEqualTo(JsonToken.FIELD_NAME);
            assertThat(parser.currentName()).isEqualTo("active");
            assertThat(parser.nextToken()).isEqualTo(JsonToken.VALUE_TRUE);
            assertThat(parser.getBooleanValue()).isTrue();

            assertThat(parser.nextToken()).isEqualTo(JsonToken.FIELD_NAME);
            assertThat(parser.currentName()).isEqualTo("threshold");
            assertThat(parser.nextToken()).isEqualTo(JsonToken.VALUE_NUMBER_FLOAT);
            assertThat(parser.getDoubleValue()).isEqualTo(0.75d);

            assertThat(parser.nextToken()).isEqualTo(JsonToken.FIELD_NAME);
            assertThat(parser.currentName()).isEqualTo("retries");
            assertThat(parser.nextToken()).isEqualTo(JsonToken.VALUE_NUMBER_INT);
            assertThat(parser.getIntValue()).isEqualTo(4);

            assertThat(parser.nextToken()).isEqualTo(JsonToken.FIELD_NAME);
            assertThat(parser.currentName()).isEqualTo("labels");
            assertThat(parser.nextToken()).isEqualTo(JsonToken.START_ARRAY);
            assertThat(parser.nextToken()).isEqualTo(JsonToken.VALUE_STRING);
            assertThat(parser.getText()).isEqualTo("core");
            assertThat(parser.nextToken()).isEqualTo(JsonToken.VALUE_STRING);
            assertThat(parser.getText()).isEqualTo("native");
            assertThat(parser.nextToken()).isEqualTo(JsonToken.END_ARRAY);

            assertThat(parser.nextToken()).isEqualTo(JsonToken.FIELD_NAME);
            assertThat(parser.currentName()).isEqualTo("nothing");
            assertThat(parser.nextToken()).isEqualTo(JsonToken.VALUE_NULL);
            assertThat(parser.nextToken()).isEqualTo(JsonToken.END_OBJECT);
            assertThat(parser.nextToken()).isNull();
        }
    }

    @Test
    void streamingGeneratorWritesNestedObjectsArraysAndBinaryValues() throws Exception {
        YAMLFactory factory = YAMLFactory.builder()
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
                .build();
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (JsonGenerator generator = factory.createGenerator(output)) {
            generator.writeStartObject();
            generator.writeStringField("kind", "Secret");
            generator.writeFieldName("data");
            generator.writeStartObject();
            generator.writeBinaryField("token", "native-yaml".getBytes(StandardCharsets.UTF_8));
            generator.writeEndObject();
            generator.writeFieldName("hosts");
            generator.writeStartArray();
            generator.writeString("api.example.test");
            generator.writeString("metrics.example.test");
            generator.writeEndArray();
            generator.writeEndObject();
        }

        byte[] serialized = output.toByteArray();
        String yaml = output.toString(StandardCharsets.UTF_8);
        JsonNode tree = new YAMLMapper().readTree(serialized);

        assertThat(yaml).doesNotStartWith("---");
        assertThat(yaml)
                .contains("kind: Secret")
                .contains("data:")
                .contains("token:")
                .contains("hosts:")
                .contains("- api.example.test")
                .contains("- metrics.example.test");
        assertThat(tree.path("kind").asText()).isEqualTo("Secret");
        byte[] expectedToken = "native-yaml".getBytes(StandardCharsets.UTF_8);
        assertThat(tree.path("data").path("token").binaryValue()).isEqualTo(expectedToken);
        assertThat(tree.path("hosts")).hasSize(2);
    }

    @Test
    void parserFeatureCanKeepBooleanLikeWordsAsStrings() throws Exception {
        YAMLFactory factory = YAMLFactory.builder()
                .enable(YAMLParser.Feature.PARSE_BOOLEAN_LIKE_WORDS_AS_STRINGS)
                .build();
        YAMLMapper mapper = YAMLMapper.builder(factory).build();

        String yaml = """
                switches:
                  power: on
                  archived: off
                  answer: yes
                  rejected: no
                regularBoolean: true
                """;

        JsonNode root = mapper.readTree(yaml);
        JsonNode switches = root.path("switches");

        assertThat(switches.path("power").isTextual()).isTrue();
        assertThat(switches.path("power").asText()).isEqualTo("on");
        assertThat(switches.path("archived").asText()).isEqualTo("off");
        assertThat(switches.path("answer").asText()).isEqualTo("yes");
        assertThat(switches.path("rejected").asText()).isEqualTo("no");
        assertThat(root.path("regularBoolean").isBoolean()).isTrue();
        assertThat(root.path("regularBoolean").asBoolean()).isTrue();
    }

    @Test
    void generatorFeaturesControlLiteralBlocksQuotingAndDocumentMarker() throws Exception {
        YAMLFactory factory = YAMLFactory.builder()
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                .enable(YAMLGenerator.Feature.LITERAL_BLOCK_STYLE)
                .enable(YAMLGenerator.Feature.ALWAYS_QUOTE_NUMBERS_AS_STRINGS)
                .enable(YAMLParser.Feature.PARSE_BOOLEAN_LIKE_WORDS_AS_STRINGS)
                .build();
        YAMLMapper mapper = YAMLMapper.builder(factory).build();
        ObjectNode document = mapper.createObjectNode();
        document.put("numericText", "007");
        document.put("booleanLikeText", "on");
        document.put("description", "line one\nline two\n");
        document.putArray("tags").add("yaml").add("jackson");

        String yaml = mapper.writeValueAsString(document);
        JsonNode roundTripped = mapper.readTree(yaml);

        assertThat(yaml).doesNotStartWith("---");
        assertThat(yaml).contains("description: |");
        assertThat(roundTripped.path("numericText").asText()).isEqualTo("007");
        assertThat(roundTripped.path("booleanLikeText").asText()).isEqualTo("on");
        assertThat(roundTripped.path("description").asText()).isEqualTo("line one\nline two\n");
        assertThat(roundTripped.path("tags").get(0).asText()).isEqualTo("yaml");
        assertThat(roundTripped.path("tags").get(1).asText()).isEqualTo("jackson");
    }

    @Test
    void mappingIteratorReadsMultipleYamlDocumentsFromOneStream() throws Exception {
        YAMLMapper mapper = new YAMLMapper();
        String yaml = """
                ---
                name: first
                priority: 1
                ---
                name: second
                priority: 2
                """;

        try (MappingIterator<JsonNode> documents = mapper.readerFor(JsonNode.class).readValues(yaml)) {
            List<JsonNode> allDocuments = documents.readAll();

            assertThat(allDocuments).hasSize(2);
            assertThat(allDocuments.get(0).path("name").asText()).isEqualTo("first");
            assertThat(allDocuments.get(0).path("priority").asInt()).isEqualTo(1);
            assertThat(allDocuments.get(1).path("name").asText()).isEqualTo("second");
            assertThat(allDocuments.get(1).path("priority").asInt()).isEqualTo(2);
        }
    }

    @Test
    void generatorAndParserSupportNativeYamlTypeIds() throws Exception {
        YAMLFactory factory = YAMLFactory.builder()
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                .enable(YAMLGenerator.Feature.USE_NATIVE_TYPE_ID)
                .build();
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (JsonGenerator generator = factory.createGenerator(output)) {
            generator.writeStartObject();
            generator.writeFieldName("resource");
            generator.writeTypeId("service");
            generator.writeStartObject();
            generator.writeStringField("name", "catalog");
            generator.writeNumberField("replicas", 3);
            generator.writeEndObject();
            generator.writeFieldName("labels");
            generator.writeTypeId("tag-list");
            generator.writeStartArray();
            generator.writeString("public");
            generator.writeString("stable");
            generator.writeEndArray();
            generator.writeEndObject();
        }

        byte[] serialized = output.toByteArray();
        String yaml = output.toString(StandardCharsets.UTF_8);

        assertThat(yaml)
                .contains("resource: !<service>")
                .contains("labels: !<tag-list>")
                .contains("- \"public\"")
                .contains("- \"stable\"");

        try (YAMLParser parser = (YAMLParser) factory.createParser(serialized)) {
            assertThat(parser.canReadTypeId()).isTrue();
            assertThat(parser.nextToken()).isEqualTo(JsonToken.START_OBJECT);
            assertThat(parser.nextToken()).isEqualTo(JsonToken.FIELD_NAME);
            assertThat(parser.currentName()).isEqualTo("resource");
            assertThat(parser.nextToken()).isEqualTo(JsonToken.START_OBJECT);
            assertThat(parser.getTypeId()).isEqualTo("service");

            assertThat(parser.nextToken()).isEqualTo(JsonToken.FIELD_NAME);
            assertThat(parser.currentName()).isEqualTo("name");
            assertThat(parser.nextToken()).isEqualTo(JsonToken.VALUE_STRING);
            assertThat(parser.getText()).isEqualTo("catalog");

            assertThat(parser.nextToken()).isEqualTo(JsonToken.FIELD_NAME);
            assertThat(parser.currentName()).isEqualTo("replicas");
            assertThat(parser.nextToken()).isEqualTo(JsonToken.VALUE_NUMBER_INT);
            assertThat(parser.getIntValue()).isEqualTo(3);

            assertThat(parser.nextToken()).isEqualTo(JsonToken.END_OBJECT);
            assertThat(parser.nextToken()).isEqualTo(JsonToken.FIELD_NAME);
            assertThat(parser.currentName()).isEqualTo("labels");
            assertThat(parser.nextToken()).isEqualTo(JsonToken.START_ARRAY);
            assertThat(parser.getTypeId()).isEqualTo("tag-list");
            assertThat(parser.nextToken()).isEqualTo(JsonToken.VALUE_STRING);
            assertThat(parser.getText()).isEqualTo("public");
            assertThat(parser.nextToken()).isEqualTo(JsonToken.VALUE_STRING);
            assertThat(parser.getText()).isEqualTo("stable");
            assertThat(parser.nextToken()).isEqualTo(JsonToken.END_ARRAY);
            assertThat(parser.nextToken()).isEqualTo(JsonToken.END_OBJECT);
            assertThat(parser.nextToken()).isNull();
        }
    }

    @Test
    void generatorAndParserSupportNativeYamlObjectIds() throws Exception {
        YAMLFactory factory = YAMLFactory.builder()
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                .enable(YAMLGenerator.Feature.USE_NATIVE_OBJECT_ID)
                .build();
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (JsonGenerator generator = factory.createGenerator(output)) {
            generator.writeStartObject();
            generator.writeFieldName("base");
            generator.writeObjectId("shared-settings");
            generator.writeStartObject();
            generator.writeStringField("host", "localhost");
            generator.writeNumberField("port", 8080);
            generator.writeEndObject();
            generator.writeFieldName("alias");
            generator.writeObjectRef("shared-settings");
            generator.writeEndObject();
        }

        byte[] serialized = output.toByteArray();
        String yaml = output.toString(StandardCharsets.UTF_8);

        assertThat(yaml)
                .contains("base: &shared-settings")
                .contains("alias: *shared-settings");

        try (YAMLParser parser = (YAMLParser) factory.createParser(serialized)) {
            assertThat(parser.canReadObjectId()).isTrue();
            assertThat(parser.nextToken()).isEqualTo(JsonToken.START_OBJECT);
            assertThat(parser.nextToken()).isEqualTo(JsonToken.FIELD_NAME);
            assertThat(parser.currentName()).isEqualTo("base");
            assertThat(parser.nextToken()).isEqualTo(JsonToken.START_OBJECT);
            assertThat(parser.getObjectId()).isEqualTo("shared-settings");

            assertThat(parser.nextToken()).isEqualTo(JsonToken.FIELD_NAME);
            assertThat(parser.currentName()).isEqualTo("host");
            assertThat(parser.nextToken()).isEqualTo(JsonToken.VALUE_STRING);
            assertThat(parser.getText()).isEqualTo("localhost");

            assertThat(parser.nextToken()).isEqualTo(JsonToken.FIELD_NAME);
            assertThat(parser.currentName()).isEqualTo("port");
            assertThat(parser.nextToken()).isEqualTo(JsonToken.VALUE_NUMBER_INT);
            assertThat(parser.getIntValue()).isEqualTo(8080);

            assertThat(parser.nextToken()).isEqualTo(JsonToken.END_OBJECT);
            assertThat(parser.nextToken()).isEqualTo(JsonToken.FIELD_NAME);
            assertThat(parser.currentName()).isEqualTo("alias");
            assertThat(parser.nextToken()).isEqualTo(JsonToken.VALUE_STRING);
            assertThat(parser.getText()).isEqualTo("shared-settings");
            assertThat(parser.nextToken()).isEqualTo(JsonToken.END_OBJECT);
            assertThat(parser.nextToken()).isNull();
        }
    }
}
