/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_dataformat.jackson_dataformat_toml;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.toml.TomlFactory;
import com.fasterxml.jackson.dataformat.toml.TomlMapper;
import com.fasterxml.jackson.dataformat.toml.TomlReadFeature;
import com.fasterxml.jackson.dataformat.toml.TomlWriteFeature;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Jackson_dataformat_tomlTest {

    @Test
    void readTreeParsesInlineTablesAndArraysOfTables() throws Exception {
        TomlMapper mapper = new TomlMapper();

        String toml = """
                title = "Workshop"
                owner = { name = "Tom Preston-Werner", organization = "Acme" }

                [[products]]
                name = "Hammer"
                sku = 738594937

                [[products]]
                name = "Nail"
                sku = 284758393
                colors = ["gray", "silver"]
                """;

        JsonNode root = mapper.readTree(toml);

        assertThat(root.path("title").asText()).isEqualTo("Workshop");
        assertThat(root.path("owner").path("name").asText()).isEqualTo("Tom Preston-Werner");
        assertThat(root.path("owner").path("organization").asText()).isEqualTo("Acme");

        JsonNode products = root.path("products");
        assertThat(products.isArray()).isTrue();
        assertThat(products.size()).isEqualTo(2);
        assertThat(products.get(0).path("name").asText()).isEqualTo("Hammer");
        assertThat(products.get(0).path("sku").asInt()).isEqualTo(738594937);
        assertThat(products.get(1).path("name").asText()).isEqualTo("Nail");
        assertThat(products.get(1).path("sku").asInt()).isEqualTo(284758393);
        assertThat(products.get(1).path("colors").get(0).asText()).isEqualTo("gray");
        assertThat(products.get(1).path("colors").get(1).asText()).isEqualTo("silver");
    }

    @Test
    void writeValueToBytesRoundTripsNestedMapsAndArrays() throws Exception {
        TomlMapper mapper = new TomlMapper();

        Map<String, Object> database = new LinkedHashMap<>();
        database.put("server", "192.168.1.1");
        database.put("ports", List.of(8001, 8001, 8002));
        database.put("connection_max", 5000);
        database.put("enabled", true);

        Map<String, Object> document = new LinkedHashMap<>();
        document.put("title", "TOML Example");
        document.put("enabled", true);
        document.put("score", 3.5d);
        document.put("ports", List.of(8001, 8001, 8002));
        document.put("database", database);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        mapper.writeValue(output, document);

        byte[] serialized = output.toByteArray();
        String toml = output.toString(StandardCharsets.UTF_8);
        Map<String, Object> roundTripped = mapper.readValue(serialized, new TypeReference<LinkedHashMap<String, Object>>() { });
        JsonNode tree = mapper.readTree(serialized);

        assertThat(toml)
                .contains("title = 'TOML Example'")
                .contains("ports = [8001, 8001, 8002]")
                .contains("database.server = '192.168.1.1'")
                .contains("database.connection_max = 5000");

        assertThat(roundTripped).containsEntry("title", "TOML Example");
        assertThat(roundTripped).containsEntry("enabled", true);
        assertThat(((Number) roundTripped.get("score")).doubleValue()).isEqualTo(3.5d);
        assertThat(roundTripped.get("ports")).isEqualTo(List.of(8001, 8001, 8002));

        Map<?, ?> roundTrippedDatabase = (Map<?, ?>) roundTripped.get("database");
        assertThat(roundTrippedDatabase.get("server")).isEqualTo("192.168.1.1");
        assertThat(roundTrippedDatabase.get("enabled")).isEqualTo(true);
        assertThat(((Number) roundTrippedDatabase.get("connection_max")).intValue()).isEqualTo(5000);

        assertThat(tree.path("database").path("server").asText()).isEqualTo("192.168.1.1");
        assertThat(tree.path("database").path("ports").size()).isEqualTo(3);
        assertThat(tree.path("database").path("enabled").asBoolean()).isTrue();
    }

    @Test
    void streamingGeneratorWritesArraysOfInlineTables() throws Exception {
        TomlFactory factory = new TomlFactory();
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (JsonGenerator generator = factory.createGenerator(output)) {
            generator.writeStartObject();
            generator.writeStringField("service", "health");
            generator.writeFieldName("checks");
            generator.writeStartArray();

            generator.writeStartObject();
            generator.writeStringField("name", "disk");
            generator.writeStringField("status", "up");
            generator.writeEndObject();

            generator.writeStartObject();
            generator.writeStringField("name", "cpu");
            generator.writeStringField("status", "warm");
            generator.writeEndObject();

            generator.writeEndArray();
            generator.writeEndObject();
        }

        byte[] serialized = output.toByteArray();
        String toml = output.toString(StandardCharsets.UTF_8);
        JsonNode tree = new TomlMapper().readTree(serialized);

        assertThat(toml)
                .contains("service = 'health'")
                .contains("checks = [{name = 'disk', status = 'up'}, {name = 'cpu', status = 'warm'}]");

        JsonNode checks = tree.path("checks");
        assertThat(checks.isArray()).isTrue();
        assertThat(checks.size()).isEqualTo(2);
        assertThat(checks.get(0).path("name").asText()).isEqualTo("disk");
        assertThat(checks.get(0).path("status").asText()).isEqualTo("up");
        assertThat(checks.get(1).path("name").asText()).isEqualTo("cpu");
        assertThat(checks.get(1).path("status").asText()).isEqualTo("warm");
    }

    @Test
    void parseJavaTimeFeatureChangesStreamingTokenValues() throws Exception {
        String toml = """
                created = 1979-05-27T07:32:00Z
                localDate = 1979-05-27
                localTime = 07:32:00
                """;

        TomlFactory temporalFactory = TomlFactory.builder()
                .enable(TomlReadFeature.PARSE_JAVA_TIME)
                .build();

        try (JsonParser parser = temporalFactory.createParser(toml)) {
            assertThat(parser.nextToken()).isEqualTo(JsonToken.START_OBJECT);

            assertThat(parser.nextToken()).isEqualTo(JsonToken.FIELD_NAME);
            assertThat(parser.currentName()).isEqualTo("created");
            assertThat(parser.nextToken()).isEqualTo(JsonToken.VALUE_EMBEDDED_OBJECT);
            assertThat(parser.getEmbeddedObject()).isEqualTo(OffsetDateTime.parse("1979-05-27T07:32:00Z"));

            assertThat(parser.nextToken()).isEqualTo(JsonToken.FIELD_NAME);
            assertThat(parser.currentName()).isEqualTo("localDate");
            assertThat(parser.nextToken()).isEqualTo(JsonToken.VALUE_EMBEDDED_OBJECT);
            assertThat(parser.getEmbeddedObject()).isEqualTo(LocalDate.parse("1979-05-27"));

            assertThat(parser.nextToken()).isEqualTo(JsonToken.FIELD_NAME);
            assertThat(parser.currentName()).isEqualTo("localTime");
            assertThat(parser.nextToken()).isEqualTo(JsonToken.VALUE_EMBEDDED_OBJECT);
            assertThat(parser.getEmbeddedObject()).isEqualTo(LocalTime.parse("07:32:00"));

            assertThat(parser.nextToken()).isEqualTo(JsonToken.END_OBJECT);
        }

        TomlFactory stringFactory = TomlFactory.builder()
                .disable(TomlReadFeature.PARSE_JAVA_TIME)
                .build();

        try (JsonParser parser = stringFactory.createParser(toml)) {
            assertThat(parser.nextToken()).isEqualTo(JsonToken.START_OBJECT);

            assertThat(parser.nextToken()).isEqualTo(JsonToken.FIELD_NAME);
            assertThat(parser.currentName()).isEqualTo("created");
            assertThat(parser.nextToken()).isEqualTo(JsonToken.VALUE_STRING);
            assertThat(parser.getText()).isEqualTo("1979-05-27T07:32:00Z");

            assertThat(parser.nextToken()).isEqualTo(JsonToken.FIELD_NAME);
            assertThat(parser.currentName()).isEqualTo("localDate");
            assertThat(parser.nextToken()).isEqualTo(JsonToken.VALUE_STRING);
            assertThat(parser.getText()).isEqualTo("1979-05-27");

            assertThat(parser.nextToken()).isEqualTo(JsonToken.FIELD_NAME);
            assertThat(parser.currentName()).isEqualTo("localTime");
            assertThat(parser.nextToken()).isEqualTo(JsonToken.VALUE_STRING);
            assertThat(parser.getText()).isEqualTo("07:32:00");

            assertThat(parser.nextToken()).isEqualTo(JsonToken.END_OBJECT);
        }
    }

    @Test
    void quotedKeysPreserveLiteralFieldNamesAcrossStreamingReadAndWrite() throws Exception {
        TomlFactory factory = new TomlFactory();
        String toml = """
                'literal.dot' = 'value'
                "spaced key" = 7
                """;

        try (JsonParser parser = factory.createParser(toml)) {
            assertThat(parser.nextToken()).isEqualTo(JsonToken.START_OBJECT);

            assertThat(parser.nextToken()).isEqualTo(JsonToken.FIELD_NAME);
            assertThat(parser.currentName()).isEqualTo("literal.dot");
            assertThat(parser.nextToken()).isEqualTo(JsonToken.VALUE_STRING);
            assertThat(parser.getText()).isEqualTo("value");

            assertThat(parser.nextToken()).isEqualTo(JsonToken.FIELD_NAME);
            assertThat(parser.currentName()).isEqualTo("spaced key");
            assertThat(parser.nextToken()).isEqualTo(JsonToken.VALUE_NUMBER_INT);
            assertThat(parser.getIntValue()).isEqualTo(7);

            assertThat(parser.nextToken()).isEqualTo(JsonToken.END_OBJECT);
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (JsonGenerator generator = factory.createGenerator(output)) {
            generator.writeStartObject();
            generator.writeStringField("literal.dot", "value");
            generator.writeNumberField("spaced key", 7);
            generator.writeEndObject();
        }

        String serialized = output.toString(StandardCharsets.UTF_8);
        JsonNode roundTripped = new TomlMapper().readTree(serialized);

        assertThat(serialized)
                .contains("'literal.dot' = 'value'")
                .contains("'spaced key' = 7");

        assertThat(roundTripped.path("literal.dot").asText()).isEqualTo("value");
        assertThat(roundTripped.path("spaced key").asInt()).isEqualTo(7);
        assertThat(roundTripped.path("literal").isMissingNode()).isTrue();
    }

    @Test
    void factoryCopiesAndRebuildsKeepWriteFeatureBehavior() throws Exception {
        TomlFactory factory = TomlFactory.builder()
                .enable(TomlReadFeature.PARSE_JAVA_TIME)
                .enable(TomlWriteFeature.FAIL_ON_NULL_WRITE)
                .build();

        assertThat(factory.getFormatName()).isEqualTo(TomlFactory.FORMAT_NAME_TOML);
        assertThat(factory.getFormatReadFeatureType()).isEqualTo(TomlReadFeature.class);
        assertThat(factory.getFormatWriteFeatureType()).isEqualTo(TomlWriteFeature.class);
        assertThat(factory.isEnabled(TomlReadFeature.PARSE_JAVA_TIME)).isTrue();
        assertThat(factory.isEnabled(TomlWriteFeature.FAIL_ON_NULL_WRITE)).isTrue();

        TomlFactory copiedFactory = factory.copy();
        assertThat(copiedFactory.isEnabled(TomlReadFeature.PARSE_JAVA_TIME)).isTrue();
        assertThat(copiedFactory.isEnabled(TomlWriteFeature.FAIL_ON_NULL_WRITE)).isTrue();

        TomlFactory rebuiltFactory = factory.rebuild()
                .disable(TomlWriteFeature.FAIL_ON_NULL_WRITE)
                .build();
        assertThat(rebuiltFactory.isEnabled(TomlWriteFeature.FAIL_ON_NULL_WRITE)).isFalse();

        TomlMapper copiedMapper = new TomlMapper(factory).copy();
        assertThat(copiedMapper.tokenStreamFactory().isEnabled(TomlReadFeature.PARSE_JAVA_TIME)).isTrue();
        assertThat(copiedMapper.tokenStreamFactory().isEnabled(TomlWriteFeature.FAIL_ON_NULL_WRITE)).isTrue();

        Map<String, Object> documentWithNull = new LinkedHashMap<>();
        documentWithNull.put("name", "value");
        documentWithNull.put("missing", null);

        assertThatThrownBy(() -> copiedMapper.writeValueAsString(documentWithNull))
                .isInstanceOf(JsonMappingException.class)
                .hasMessageContaining("FAIL_ON_NULL_WRITE");
    }
}
