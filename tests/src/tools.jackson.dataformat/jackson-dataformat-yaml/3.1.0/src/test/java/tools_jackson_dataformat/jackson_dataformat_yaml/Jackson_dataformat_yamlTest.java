/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tools_jackson_dataformat.jackson_dataformat_yaml;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.snakeyaml.engine.v2.api.LoadSettings;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.dataformat.yaml.JacksonYAMLParseException;
import tools.jackson.dataformat.yaml.YAMLAnchorReplayingFactory;
import tools.jackson.dataformat.yaml.YAMLFactory;
import tools.jackson.dataformat.yaml.YAMLGenerator;
import tools.jackson.dataformat.yaml.YAMLMapper;
import tools.jackson.dataformat.yaml.YAMLParser;
import tools.jackson.dataformat.yaml.YAMLReadFeature;
import tools.jackson.dataformat.yaml.YAMLWriteFeature;
import tools.jackson.dataformat.yaml.util.StringQuotingChecker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Jackson_dataformat_yamlTest {

    @Test
    void yamlMapperRoundTripsNestedMapsListsScalarsAndBinaryValues() throws Exception {
        YAMLMapper mapper = YAMLMapper.builder()
                .disable(YAMLWriteFeature.WRITE_DOC_START_MARKER)
                .build();
        byte[] certificate = new byte[] {1, 3, 5, 8, 13, 21};

        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("enabled", true);
        nested.put("limit", new BigInteger("123456789012345678901234567890"));
        nested.put("ratio", new BigDecimal("12.375"));

        Map<String, Object> document = new LinkedHashMap<>();
        document.put("service", "payments");
        document.put("version", 3);
        document.put("labels", List.of("yaml", "jackson", "native-image"));
        document.put("certificate", certificate);
        document.put("nested", nested);
        document.put("nothing", null);

        String encoded = mapper.writeValueAsString(document);
        JsonNode tree = mapper.readTree(encoded);
        Map<String, Object> roundTripped = mapper.readValue(
                encoded, new TypeReference<LinkedHashMap<String, Object>>() { });

        assertThat(encoded).doesNotStartWith("---");
        assertThat(tree.path("service").asString()).isEqualTo("payments");
        assertThat(tree.path("version").asInt()).isEqualTo(3);
        assertThat(tree.path("labels")).hasSize(3);
        assertThat(tree.path("labels").get(2).asString()).isEqualTo("native-image");
        assertThat(tree.path("certificate").binaryValue()).isEqualTo(certificate);
        assertThat(tree.path("nested").path("enabled").asBoolean()).isTrue();
        assertThat(tree.path("nested").path("limit").bigIntegerValue())
                .isEqualTo(new BigInteger("123456789012345678901234567890"));
        assertThat(tree.path("nested").path("ratio").decimalValue()).isEqualByComparingTo("12.375");
        assertThat(tree.path("nothing").isNull()).isTrue();

        assertThat(roundTripped).containsEntry("service", "payments");
        assertThat(roundTripped).containsEntry("version", 3);
        assertThat(roundTripped.get("labels")).isEqualTo(List.of("yaml", "jackson", "native-image"));
        assertThat((byte[]) roundTripped.get("certificate")).isEqualTo(certificate);
        Map<?, ?> nestedRoundTripped = (Map<?, ?>) roundTripped.get("nested");
        assertThat(nestedRoundTripped.get("enabled")).isEqualTo(true);
    }

    @Test
    void treeModelWritesObjectNodesArraysNullsAndBinaryScalars() throws Exception {
        YAMLMapper mapper = YAMLMapper.builder()
                .disable(YAMLWriteFeature.WRITE_DOC_START_MARKER)
                .build();
        ObjectNode root = mapper.createObjectNode();
        root.put("name", "metrics-batch");
        root.put("count", 2);
        root.put("complete", false);
        root.put("checksum", new byte[] {(byte) 0xCA, (byte) 0xFE, 0x42});
        root.putNull("optional");
        ArrayNode samples = root.putArray("samples");
        samples.addObject()
                .put("host", "alpha")
                .put("value", 1.25d);
        samples.addObject()
                .put("host", "beta")
                .put("value", 2.5d);

        byte[] encoded = mapper.writeValueAsBytes(root);
        JsonNode decoded = mapper.readTree(encoded);

        assertThat(decoded.path("name").asString()).isEqualTo("metrics-batch");
        assertThat(decoded.path("count").asInt()).isEqualTo(2);
        assertThat(decoded.path("complete").asBoolean()).isFalse();
        assertThat(decoded.path("checksum").binaryValue()).isEqualTo(new byte[] {(byte) 0xCA, (byte) 0xFE, 0x42});
        assertThat(decoded.path("optional").isNull()).isTrue();
        assertThat(decoded.path("samples")).hasSize(2);
        assertThat(decoded.path("samples").get(0).path("host").asString()).isEqualTo("alpha");
        assertThat(decoded.path("samples").get(0).path("value").asDouble()).isEqualTo(1.25d);
        assertThat(decoded.path("samples").get(1).path("host").asString()).isEqualTo("beta");
        assertThat(decoded.path("samples").get(1).path("value").asDouble()).isEqualTo(2.5d);
    }

    @Test
    void streamingGeneratorAndParserExposeExpectedTokenSequenceAndTypedValues() throws Exception {
        YAMLFactory factory = YAMLFactory.builder()
                .disable(YAMLWriteFeature.WRITE_DOC_START_MARKER)
                .enable(YAMLWriteFeature.MINIMIZE_QUOTES)
                .build();
        StringWriter writer = new StringWriter();

        try (YAMLGenerator generator = (YAMLGenerator) factory.createGenerator(writer)) {
            generator.writeStartObject();
            generator.writeStringProperty("name", "inventory");
            generator.writeNumberProperty("count", 42);
            generator.writeName("active");
            generator.writeBoolean(true);
            generator.writeName("temperatures");
            generator.writeStartArray();
            generator.writeNumber(19.5d);
            generator.writeNumber(20.0d);
            generator.writeNumber(21.25d);
            generator.writeEndArray();
            generator.writeName("metadata");
            generator.writeStartObject();
            generator.writeName("nullable");
            generator.writeNull();
            generator.writePropertyId(7L);
            generator.writeString("numeric-property-id");
            generator.writeEndObject();
            generator.writeName("big");
            generator.writeNumber(new BigInteger("18446744073709551616"));
            generator.writeEndObject();
        }

        try (JsonParser parser = factory.createParser(new StringReader(writer.toString()))) {
            assertThat(parser.nextToken()).isEqualTo(JsonToken.START_OBJECT);

            assertThat(parser.nextName()).isEqualTo("name");
            assertThat(parser.nextStringValue()).isEqualTo("inventory");

            assertThat(parser.nextName()).isEqualTo("count");
            assertThat(parser.nextIntValue(-1)).isEqualTo(42);

            assertThat(parser.nextName()).isEqualTo("active");
            assertThat(parser.nextBooleanValue()).isTrue();

            assertThat(parser.nextName()).isEqualTo("temperatures");
            assertThat(parser.nextToken()).isEqualTo(JsonToken.START_ARRAY);
            assertThat(parser.nextToken()).isEqualTo(JsonToken.VALUE_NUMBER_FLOAT);
            assertThat(parser.getDoubleValue()).isEqualTo(19.5d);
            assertThat(parser.nextToken()).isEqualTo(JsonToken.VALUE_NUMBER_FLOAT);
            assertThat(parser.getDoubleValue()).isEqualTo(20.0d);
            assertThat(parser.nextToken()).isEqualTo(JsonToken.VALUE_NUMBER_FLOAT);
            assertThat(parser.getDoubleValue()).isEqualTo(21.25d);
            assertThat(parser.nextToken()).isEqualTo(JsonToken.END_ARRAY);

            assertThat(parser.nextName()).isEqualTo("metadata");
            assertThat(parser.nextToken()).isEqualTo(JsonToken.START_OBJECT);
            assertThat(parser.nextName()).isEqualTo("nullable");
            assertThat(parser.nextToken()).isEqualTo(JsonToken.VALUE_NULL);
            assertThat(parser.nextName()).isEqualTo("7");
            assertThat(parser.nextStringValue()).isEqualTo("numeric-property-id");
            assertThat(parser.nextToken()).isEqualTo(JsonToken.END_OBJECT);

            assertThat(parser.nextName()).isEqualTo("big");
            assertThat(parser.nextToken()).isEqualTo(JsonToken.VALUE_NUMBER_INT);
            assertThat(parser.getBigIntegerValue()).isEqualTo(new BigInteger("18446744073709551616"));

            assertThat(parser.nextToken()).isEqualTo(JsonToken.END_OBJECT);
            assertThat(parser.nextToken()).isNull();
        }
    }

    @Test
    void parserRecognizesYamlBooleanIntegerFloatBinaryAndNullScalars() throws Exception {
        String yaml = """
                active: true
                disabled: false
                decimal: 12.75
                binary: !!int 0b101010
                octal: !!int 052
                hex: !!int 0x2A
                empty: null
                bytes: !!binary |
                  AQIDBAU=
                """;

        try (JsonParser parser = YAMLFactory.builder().build().createParser(yaml)) {
            assertThat(parser.nextToken()).isEqualTo(JsonToken.START_OBJECT);
            assertThat(parser.nextName()).isEqualTo("active");
            assertThat(parser.nextBooleanValue()).isTrue();
            assertThat(parser.nextName()).isEqualTo("disabled");
            assertThat(parser.nextBooleanValue()).isFalse();
            assertThat(parser.nextName()).isEqualTo("decimal");
            assertThat(parser.nextToken()).isEqualTo(JsonToken.VALUE_NUMBER_FLOAT);
            assertThat(parser.getDecimalValue()).isEqualByComparingTo("12.75");
            assertThat(parser.nextName()).isEqualTo("binary");
            assertThat(parser.nextIntValue(-1)).isEqualTo(42);
            assertThat(parser.nextName()).isEqualTo("octal");
            assertThat(parser.nextIntValue(-1)).isEqualTo(42);
            assertThat(parser.nextName()).isEqualTo("hex");
            assertThat(parser.nextIntValue(-1)).isEqualTo(42);
            assertThat(parser.nextName()).isEqualTo("empty");
            assertThat(parser.nextToken()).isEqualTo(JsonToken.VALUE_NULL);
            assertThat(parser.nextName()).isEqualTo("bytes");
            assertThat(parser.nextToken()).isEqualTo(JsonToken.VALUE_EMBEDDED_OBJECT);
            assertThat(parser.getBinaryValue()).containsExactly((byte) 1, (byte) 2, (byte) 3, (byte) 4, (byte) 5);
            assertThat(parser.nextToken()).isEqualTo(JsonToken.END_OBJECT);
            assertThat(parser.nextToken()).isNull();
        }
    }

    @Test
    void binaryValuesCanBeWrittenFromAndReadToStreams() throws Exception {
        YAMLFactory factory = YAMLFactory.builder()
                .disable(YAMLWriteFeature.WRITE_DOC_START_MARKER)
                .build();
        byte[] payload = new byte[96];
        for (int index = 0; index < payload.length; index++) {
            payload[index] = (byte) (index * 5 + 11);
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (JsonGenerator generator = factory.createGenerator(output)) {
            generator.writeStartObject();
            generator.writeStringProperty("name", "firmware");
            generator.writeName("bytes");
            generator.writeBinary(payload);
            generator.writeEndObject();
        }

        try (JsonParser parser = factory.createParser(new ByteArrayInputStream(output.toByteArray()))) {
            assertThat(parser.nextToken()).isEqualTo(JsonToken.START_OBJECT);
            assertThat(parser.nextName()).isEqualTo("name");
            assertThat(parser.nextStringValue()).isEqualTo("firmware");
            assertThat(parser.nextName()).isEqualTo("bytes");
            assertThat(parser.nextToken()).isEqualTo(JsonToken.VALUE_EMBEDDED_OBJECT);

            ByteArrayOutputStream decoded = new ByteArrayOutputStream();
            int bytesRead = parser.readBinaryValue(decoded);

            assertThat(bytesRead).isEqualTo(payload.length);
            assertThat(decoded.toByteArray()).isEqualTo(payload);
            assertThat(parser.nextToken()).isEqualTo(JsonToken.END_OBJECT);
            assertThat(parser.nextToken()).isNull();
        }
    }

    @Test
    void primitiveArraysCanBeWrittenInBulkWithOffsets() throws Exception {
        YAMLFactory factory = YAMLFactory.builder()
                .disable(YAMLWriteFeature.WRITE_DOC_START_MARKER)
                .build();
        int[] counts = new int[] {99, 3, 5, 8, 99};
        long[] timestamps = new long[] {1_700_000_001L, 1_700_000_002L, 1_700_000_003L};
        double[] ratios = new double[] {-1.0d, 0.25d, 0.5d, 1.0d};
        StringWriter writer = new StringWriter();

        try (JsonGenerator generator = factory.createGenerator(writer)) {
            generator.writeStartObject();
            generator.writeName("counts");
            generator.writeArray(counts, 1, 3);
            generator.writeName("timestamps");
            generator.writeArray(timestamps, 0, 2);
            generator.writeName("ratios");
            generator.writeArray(ratios, 1, 2);
            generator.writeEndObject();
        }

        try (JsonParser parser = factory.createParser(writer.toString().getBytes(StandardCharsets.UTF_8))) {
            assertThat(parser.nextToken()).isEqualTo(JsonToken.START_OBJECT);

            assertThat(parser.nextName()).isEqualTo("counts");
            assertThat(parser.nextToken()).isEqualTo(JsonToken.START_ARRAY);
            assertThat(parser.nextIntValue(-1)).isEqualTo(3);
            assertThat(parser.nextIntValue(-1)).isEqualTo(5);
            assertThat(parser.nextIntValue(-1)).isEqualTo(8);
            assertThat(parser.nextToken()).isEqualTo(JsonToken.END_ARRAY);

            assertThat(parser.nextName()).isEqualTo("timestamps");
            assertThat(parser.nextToken()).isEqualTo(JsonToken.START_ARRAY);
            assertThat(parser.nextLongValue(-1L)).isEqualTo(1_700_000_001L);
            assertThat(parser.nextLongValue(-1L)).isEqualTo(1_700_000_002L);
            assertThat(parser.nextToken()).isEqualTo(JsonToken.END_ARRAY);

            assertThat(parser.nextName()).isEqualTo("ratios");
            assertThat(parser.nextToken()).isEqualTo(JsonToken.START_ARRAY);
            assertThat(parser.nextToken()).isEqualTo(JsonToken.VALUE_NUMBER_FLOAT);
            assertThat(parser.getDoubleValue()).isEqualTo(0.25d);
            assertThat(parser.nextToken()).isEqualTo(JsonToken.VALUE_NUMBER_FLOAT);
            assertThat(parser.getDoubleValue()).isEqualTo(0.5d);
            assertThat(parser.nextToken()).isEqualTo(JsonToken.END_ARRAY);

            assertThat(parser.nextToken()).isEqualTo(JsonToken.END_OBJECT);
            assertThat(parser.nextToken()).isNull();
        }
    }

    @Test
    void writeFeaturesControlDocumentMarkerQuotingAndLiteralBlockStyle() throws Exception {
        YAMLMapper markerMapper = YAMLMapper.builder()
                .enable(YAMLWriteFeature.WRITE_DOC_START_MARKER)
                .enable(YAMLWriteFeature.MINIMIZE_QUOTES)
                .enable(YAMLWriteFeature.ALWAYS_QUOTE_NUMBERS_AS_STRINGS)
                .enable(YAMLWriteFeature.LITERAL_BLOCK_STYLE)
                .build();

        Map<String, Object> document = new LinkedHashMap<>();
        document.put("numericString", "12345");
        document.put("plainString", "service-name");
        document.put("description", "line one\nline two\nline three");

        String yaml = markerMapper.writeValueAsString(document);
        JsonNode decoded = markerMapper.readTree(yaml);

        assertThat(yaml).startsWith("---");
        assertThat(yaml).contains("numericString: \"12345\"");
        assertThat(yaml).contains("plainString: service-name");
        assertThat(yaml).contains("description: |");
        assertThat(decoded.path("numericString").asString()).isEqualTo("12345");
        assertThat(decoded.path("plainString").asString()).isEqualTo("service-name");
        assertThat(decoded.path("description").asString()).isEqualTo("line one\nline two\nline three");

        YAMLMapper minimizedMapper = YAMLMapper.builder()
                .disable(YAMLWriteFeature.WRITE_DOC_START_MARKER)
                .enable(YAMLWriteFeature.MINIMIZE_QUOTES)
                .build();
        String minimized = minimizedMapper.writeValueAsString(Map.of("plain", "service-name"));

        assertThat(minimized).isEqualTo("plain: service-name\n");
    }

    @Test
    void readFeaturesCanTreatEmptyDocumentsAsObjectsAndExposeEmptyStringConfiguration() throws Exception {
        YAMLFactory emptyDocumentFactory = YAMLFactory.builder()
                .enable(YAMLReadFeature.EMPTY_DOCUMENT_AS_EMPTY_OBJECT)
                .build();

        try (JsonParser parser = emptyDocumentFactory.createParser("")) {
            assertThat(parser.nextToken()).isEqualTo(JsonToken.START_OBJECT);
            assertThat(parser.nextToken()).isEqualTo(JsonToken.END_OBJECT);
            assertThat(parser.nextToken()).isNull();
        }

        YAMLMapper emptyStringMapper = YAMLMapper.builder()
                .enable(YAMLReadFeature.EMPTY_STRING_AS_NULL)
                .build();
        JsonNode converted = emptyStringMapper.readTree("empty: \"\"\ntext: value\n");

        assertThat(emptyStringMapper.isEnabled(YAMLReadFeature.EMPTY_STRING_AS_NULL)).isTrue();
        assertThat(converted.path("empty").asString()).isEmpty();
        assertThat(converted.path("text").asString()).isEqualTo("value");
    }

    @Test
    void customLoadSettingsRejectDuplicateKeysWhileAllowingUniqueMappings() throws Exception {
        LoadSettings loadSettings = LoadSettings.builder()
                .setAllowDuplicateKeys(false)
                .build();
        YAMLFactory factory = YAMLFactory.builder()
                .loadSettings(loadSettings)
                .build();
        YAMLMapper mapper = YAMLMapper.builder(factory).build();

        JsonNode uniqueMapping = mapper.readTree("first: alpha\nsecond: beta\n");

        assertThat(uniqueMapping.path("first").asString()).isEqualTo("alpha");
        assertThat(uniqueMapping.path("second").asString()).isEqualTo("beta");
        assertThatThrownBy(() -> mapper.readTree("name: primary\nname: duplicate\n"))
                .isInstanceOf(JacksonException.class)
                .hasMessageContaining("Duplicate Object property \"name\"");
    }

    @Test
    void anchorReplayingFactoryExpandsAliasesWhenBindingToTreeModel() throws Exception {
        YAMLMapper mapper = YAMLMapper.builder(new YAMLAnchorReplayingFactory())
                .build();
        String yaml = """
                common: &common
                  retries: 3
                  timeout: 250
                services:
                  - name: api
                    config: *common
                  - name: worker
                    config: *common
                """;

        JsonNode root = mapper.readTree(yaml);

        assertThat(root.path("common").path("retries").asInt()).isEqualTo(3);
        assertThat(root.path("services")).hasSize(2);
        assertThat(root.path("services").get(0).path("config").path("timeout").asInt()).isEqualTo(250);
        assertThat(root.path("services").get(1).path("config").path("retries").asInt()).isEqualTo(3);
    }

    @Test
    void parserExposesAliasesObjectIdsTypeIdsAndCurrentLocations() throws Exception {
        YAMLFactory factory = YAMLFactory.builder()
                .enable(YAMLWriteFeature.USE_NATIVE_OBJECT_ID)
                .enable(YAMLWriteFeature.USE_NATIVE_TYPE_ID)
                .build();
        String yaml = """
                referenced: &server !server-type
                  host: localhost
                alias: *server
                """;

        try (YAMLParser parser = (YAMLParser) factory.createParser(yaml)) {
            assertThat(parser.canReadObjectId()).isTrue();
            assertThat(parser.canReadTypeId()).isTrue();
            assertThat(parser.currentLocation()).isNotNull();

            assertThat(parser.nextToken()).isEqualTo(JsonToken.START_OBJECT);
            assertThat(parser.nextName()).isEqualTo("referenced");
            assertThat(parser.nextToken()).isEqualTo(JsonToken.START_OBJECT);
            assertThat(parser.getObjectId()).isEqualTo("server");
            assertThat(parser.getTypeId()).isEqualTo("server-type");
            assertThat(parser.isCurrentAlias()).isFalse();
            assertThat(parser.nextName()).isEqualTo("host");
            assertThat(parser.nextStringValue()).isEqualTo("localhost");
            assertThat(parser.nextToken()).isEqualTo(JsonToken.END_OBJECT);

            assertThat(parser.nextName()).isEqualTo("alias");
            assertThat(parser.nextToken()).isEqualTo(JsonToken.VALUE_STRING);
            assertThat(parser.isCurrentAlias()).isTrue();
            assertThat(parser.getObjectId()).isNull();
            assertThat(parser.getString()).isEqualTo("server");

            assertThat(parser.nextToken()).isEqualTo(JsonToken.END_OBJECT);
            assertThat(parser.nextToken()).isNull();
        }
    }

    @Test
    void factoryReportsYamlCapabilitiesAndCopiedConfiguration() throws Exception {
        YAMLFactory factory = YAMLFactory.builder()
                .disable(YAMLWriteFeature.WRITE_DOC_START_MARKER)
                .enable(YAMLWriteFeature.MINIMIZE_QUOTES)
                .enable(YAMLReadFeature.EMPTY_STRING_AS_NULL)
                .stringQuotingChecker(StringQuotingChecker.Default.instance())
                .build();
        YAMLFactory copied = factory.copy();
        YAMLFactory snapshotted = (YAMLFactory) factory.snapshot();

        assertThat(factory.getFormatName()).isEqualTo(YAMLFactory.FORMAT_NAME_YAML);
        assertThat(factory.canUseCharArrays()).isFalse();
        assertThat(factory.canParseAsync()).isFalse();
        assertThat(factory.getFormatWriteFeatureType()).isEqualTo(YAMLWriteFeature.class);
        assertThat(factory.getFormatReadFeatureType()).isEqualTo(YAMLReadFeature.class);
        assertThat(copied.isEnabled(YAMLWriteFeature.WRITE_DOC_START_MARKER)).isFalse();
        assertThat(copied.isEnabled(YAMLWriteFeature.MINIMIZE_QUOTES)).isTrue();
        assertThat(snapshotted.isEnabled(YAMLReadFeature.EMPTY_STRING_AS_NULL)).isTrue();

        YAMLMapper mapper = YAMLMapper.builder(copied).build();
        String encoded = mapper.writeValueAsString(Map.of("answer", 42));
        JsonNode decoded = mapper.readTree(encoded);

        assertThat(encoded).isEqualTo("answer: 42\n");
        assertThat(decoded.path("answer").asInt()).isEqualTo(42);
    }

    @Test
    void factoryRebuildPreservesExistingSettingsAndAllowsFeatureChanges() throws Exception {
        YAMLFactory original = YAMLFactory.builder()
                .disable(YAMLWriteFeature.WRITE_DOC_START_MARKER)
                .enable(YAMLReadFeature.EMPTY_STRING_AS_NULL)
                .build();
        YAMLFactory rebuilt = original.rebuild()
                .enable(YAMLWriteFeature.WRITE_DOC_START_MARKER)
                .disable(YAMLReadFeature.EMPTY_STRING_AS_NULL)
                .build();

        assertThat(original.isEnabled(YAMLWriteFeature.WRITE_DOC_START_MARKER)).isFalse();
        assertThat(original.isEnabled(YAMLReadFeature.EMPTY_STRING_AS_NULL)).isTrue();
        assertThat(rebuilt.isEnabled(YAMLWriteFeature.WRITE_DOC_START_MARKER)).isTrue();
        assertThat(rebuilt.isEnabled(YAMLReadFeature.EMPTY_STRING_AS_NULL)).isFalse();

        YAMLMapper mapper = YAMLMapper.builder(rebuilt).build();
        String yaml = mapper.writeValueAsString(Map.of("value", ""));
        JsonNode decoded = mapper.readTree(yaml);

        assertThat(yaml).startsWith("---");
        assertThat(decoded.path("value").asString()).isEmpty();
    }

    @Test
    void invalidYamlProducesJacksonYamlParseExceptionWithLocation() {
        YAMLMapper mapper = YAMLMapper.builder().build();
        String invalidYaml = """
                root:
                  - ok
                  - [unterminated
                """;

        assertThatThrownBy(() -> mapper.readTree(invalidYaml))
                .isInstanceOf(JacksonYAMLParseException.class)
                .isInstanceOf(JacksonException.class);
    }
}
