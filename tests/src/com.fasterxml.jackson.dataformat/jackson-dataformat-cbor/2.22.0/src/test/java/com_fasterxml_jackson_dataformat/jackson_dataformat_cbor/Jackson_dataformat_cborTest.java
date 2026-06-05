/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_dataformat.jackson_dataformat_cbor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.format.InputAccessor;
import com.fasterxml.jackson.core.format.MatchStrength;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.dataformat.cbor.CBORGenerator;
import com.fasterxml.jackson.dataformat.cbor.CBORParser;
import com.fasterxml.jackson.dataformat.cbor.databind.CBORMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Jackson_dataformat_cborTest {

    @Test
    void cborMapperRoundTripsNestedMapsListsScalarsAndBinaryValues() throws Exception {
        CBORMapper mapper = new CBORMapper();
        byte[] payload = new byte[] {1, 3, 5, 8, 13, 21};

        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("enabled", true);
        nested.put("limit", new BigInteger("123456789012345678901234567890"));
        nested.put("ratio", new BigDecimal("12.375"));

        Map<String, Object> document = new LinkedHashMap<>();
        document.put("service", "telemetry");
        document.put("version", 3);
        document.put("labels", List.of("cbor", "jackson", "native-image"));
        document.put("payload", payload);
        document.put("nested", nested);
        document.put("nothing", null);

        byte[] encoded = mapper.writeValueAsBytes(document);
        JsonNode tree = mapper.readTree(encoded);
        Map<String, Object> roundTripped = mapper.readValue(
                encoded, new TypeReference<LinkedHashMap<String, Object>>() { });

        assertThat(tree.path("service").asText()).isEqualTo("telemetry");
        assertThat(tree.path("version").asInt()).isEqualTo(3);
        assertThat(tree.path("labels")).hasSize(3);
        assertThat(tree.path("labels").get(2).asText()).isEqualTo("native-image");
        assertThat(tree.path("payload").binaryValue()).isEqualTo(payload);
        assertThat(tree.path("nested").path("enabled").asBoolean()).isTrue();
        assertThat(tree.path("nested").path("limit").bigIntegerValue())
                .isEqualTo(new BigInteger("123456789012345678901234567890"));
        assertThat(tree.path("nested").path("ratio").decimalValue()).isEqualByComparingTo("12.375");
        assertThat(tree.path("nothing").isNull()).isTrue();

        assertThat(roundTripped).containsEntry("service", "telemetry");
        assertThat(roundTripped).containsEntry("version", 3);
        assertThat(roundTripped.get("labels")).isEqualTo(List.of("cbor", "jackson", "native-image"));
        assertThat((byte[]) roundTripped.get("payload")).isEqualTo(payload);
        Map<?, ?> nestedRoundTripped = (Map<?, ?>) roundTripped.get("nested");
        assertThat(nestedRoundTripped.get("enabled")).isEqualTo(true);
    }

    @Test
    void treeModelWritesObjectNodesArraysAndEmbeddedBinaryValues() throws Exception {
        CBORMapper mapper = CBORMapper.builder().build();
        ObjectNode root = mapper.createObjectNode();
        root.put("name", "metrics-batch");
        root.put("count", 2);
        root.put("complete", false);
        root.put("checksum", new byte[] {(byte) 0xCA, (byte) 0xFE, 0x42});
        ArrayNode samples = root.putArray("samples");
        samples.addObject()
                .put("host", "alpha")
                .put("value", 1.25d);
        samples.addObject()
                .put("host", "beta")
                .put("value", 2.5d);

        byte[] encoded = mapper.writeValueAsBytes(root);
        JsonNode decoded = mapper.readTree(encoded);

        assertThat(decoded.path("name").asText()).isEqualTo("metrics-batch");
        assertThat(decoded.path("count").asInt()).isEqualTo(2);
        assertThat(decoded.path("complete").asBoolean()).isFalse();
        assertThat(decoded.path("checksum").binaryValue()).isEqualTo(new byte[] {(byte) 0xCA, (byte) 0xFE, 0x42});
        assertThat(decoded.path("samples")).hasSize(2);
        assertThat(decoded.path("samples").get(0).path("host").asText()).isEqualTo("alpha");
        assertThat(decoded.path("samples").get(0).path("value").asDouble()).isEqualTo(1.25d);
        assertThat(decoded.path("samples").get(1).path("host").asText()).isEqualTo("beta");
        assertThat(decoded.path("samples").get(1).path("value").asDouble()).isEqualTo(2.5d);
    }

    @Test
    void streamingGeneratorAndParserExposeExpectedTokenSequenceAndTypedValues() throws Exception {
        CBORFactory factory = new CBORFactory();
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (CBORGenerator generator = factory.createGenerator(output)) {
            generator.writeStartObject();
            generator.writeStringField("name", "inventory");
            generator.writeNumberField("count", 42);
            generator.writeFieldName("active");
            generator.writeBoolean(true);
            generator.writeFieldName("temperatures");
            generator.writeStartArray();
            generator.writeNumber(19.5d);
            generator.writeNumber(20.0d);
            generator.writeNumber(21.25d);
            generator.writeEndArray();
            generator.writeFieldName("metadata");
            generator.writeStartObject();
            generator.writeFieldName("nullable");
            generator.writeNull();
            generator.writeFieldId(7L);
            generator.writeString("numeric-field-name");
            generator.writeEndObject();
            generator.writeFieldName("big");
            generator.writeNumber(new BigInteger("18446744073709551616"));
            generator.writeEndObject();
        }

        try (CBORParser parser = factory.createParser(output.toByteArray())) {
            assertThat(parser.nextToken()).isEqualTo(JsonToken.START_OBJECT);

            assertThat(parser.nextFieldName()).isEqualTo("name");
            assertThat(parser.nextTextValue()).isEqualTo("inventory");

            assertThat(parser.nextFieldName()).isEqualTo("count");
            assertThat(parser.nextIntValue(-1)).isEqualTo(42);

            assertThat(parser.nextFieldName()).isEqualTo("active");
            assertThat(parser.nextBooleanValue()).isTrue();

            assertThat(parser.nextFieldName()).isEqualTo("temperatures");
            assertThat(parser.nextToken()).isEqualTo(JsonToken.START_ARRAY);
            assertThat(parser.nextToken()).isEqualTo(JsonToken.VALUE_NUMBER_FLOAT);
            assertThat(parser.getDoubleValue()).isEqualTo(19.5d);
            assertThat(parser.nextToken()).isEqualTo(JsonToken.VALUE_NUMBER_FLOAT);
            assertThat(parser.getDoubleValue()).isEqualTo(20.0d);
            assertThat(parser.nextToken()).isEqualTo(JsonToken.VALUE_NUMBER_FLOAT);
            assertThat(parser.getDoubleValue()).isEqualTo(21.25d);
            assertThat(parser.nextToken()).isEqualTo(JsonToken.END_ARRAY);

            assertThat(parser.nextFieldName()).isEqualTo("metadata");
            assertThat(parser.nextToken()).isEqualTo(JsonToken.START_OBJECT);
            assertThat(parser.nextFieldName()).isEqualTo("nullable");
            assertThat(parser.nextToken()).isEqualTo(JsonToken.VALUE_NULL);
            assertThat(parser.nextFieldName()).isEqualTo("7");
            assertThat(parser.nextTextValue()).isEqualTo("numeric-field-name");
            assertThat(parser.nextToken()).isEqualTo(JsonToken.END_OBJECT);

            assertThat(parser.nextFieldName()).isEqualTo("big");
            assertThat(parser.nextToken()).isEqualTo(JsonToken.VALUE_NUMBER_INT);
            assertThat(parser.getBigIntegerValue()).isEqualTo(new BigInteger("18446744073709551616"));

            assertThat(parser.nextToken()).isEqualTo(JsonToken.END_OBJECT);
            assertThat(parser.nextToken()).isNull();
        }
    }

    @Test
    void binaryValuesCanBeWrittenFromAndReadToStreams() throws Exception {
        CBORFactory factory = new CBORFactory();
        byte[] payload = new byte[128];
        for (int index = 0; index < payload.length; index++) {
            payload[index] = (byte) (index * 3 + 1);
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (JsonGenerator generator = factory.createGenerator(output)) {
            generator.writeStartObject();
            generator.writeStringField("name", "firmware");
            generator.writeFieldName("bytes");
            int bytesWritten = generator.writeBinary(new ByteArrayInputStream(payload), payload.length);
            generator.writeEndObject();

            assertThat(bytesWritten).isEqualTo(payload.length);
        }

        try (JsonParser parser = factory.createParser(new ByteArrayInputStream(output.toByteArray()))) {
            assertThat(parser.nextToken()).isEqualTo(JsonToken.START_OBJECT);
            assertThat(parser.nextFieldName()).isEqualTo("name");
            assertThat(parser.nextTextValue()).isEqualTo("firmware");
            assertThat(parser.nextFieldName()).isEqualTo("bytes");
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
    void streamingGeneratorWritesPrimitiveArraysInBulkWithOffsets() throws Exception {
        CBORFactory factory = new CBORFactory();
        int[] counts = new int[] {99, 3, 5, 8, 99};
        long[] timestamps = new long[] {1_700_000_001L, 1_700_000_002L, 1_700_000_003L};
        double[] ratios = new double[] {-1.0d, 0.25d, 0.5d, 1.0d};
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (CBORGenerator generator = factory.createGenerator(output)) {
            generator.writeStartObject();
            generator.writeFieldName("counts");
            generator.writeArray(counts, 1, 3);
            generator.writeFieldName("timestamps");
            generator.writeArray(timestamps, 0, 2);
            generator.writeFieldName("ratios");
            generator.writeArray(ratios, 1, 2);
            generator.writeEndObject();
        }

        try (CBORParser parser = factory.createParser(output.toByteArray())) {
            assertThat(parser.nextToken()).isEqualTo(JsonToken.START_OBJECT);

            assertThat(parser.nextFieldName()).isEqualTo("counts");
            assertThat(parser.nextToken()).isEqualTo(JsonToken.START_ARRAY);
            assertThat(parser.nextIntValue(-1)).isEqualTo(3);
            assertThat(parser.nextIntValue(-1)).isEqualTo(5);
            assertThat(parser.nextIntValue(-1)).isEqualTo(8);
            assertThat(parser.nextToken()).isEqualTo(JsonToken.END_ARRAY);

            assertThat(parser.nextFieldName()).isEqualTo("timestamps");
            assertThat(parser.nextToken()).isEqualTo(JsonToken.START_ARRAY);
            assertThat(parser.nextLongValue(-1L)).isEqualTo(1_700_000_001L);
            assertThat(parser.nextLongValue(-1L)).isEqualTo(1_700_000_002L);
            assertThat(parser.nextToken()).isEqualTo(JsonToken.END_ARRAY);

            assertThat(parser.nextFieldName()).isEqualTo("ratios");
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
    void cborTagsArePreservedForTaggedScalarValues() throws Exception {
        CBORFactory factory = new CBORFactory();
        String uuid = "f81d4fae-7dec-11d0-a765-00a0c91e6bf6";
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (CBORGenerator generator = factory.createGenerator(output)) {
            generator.writeStartArray();
            generator.writeTag(1);
            generator.writeNumber(1_700_000_000L);
            generator.writeTag(37);
            generator.writeString(uuid);
            generator.writeEndArray();
        }

        try (CBORParser parser = factory.createParser(output.toByteArray())) {
            assertThat(parser.nextToken()).isEqualTo(JsonToken.START_ARRAY);

            assertThat(parser.nextToken()).isEqualTo(JsonToken.VALUE_NUMBER_INT);
            assertThat(parser.getCurrentTag()).isEqualTo(1);
            assertThat(parser.getLongValue()).isEqualTo(1_700_000_000L);

            assertThat(parser.nextToken()).isEqualTo(JsonToken.VALUE_STRING);
            assertThat(parser.getCurrentTag()).isEqualTo(37);
            assertThat(parser.getText()).isEqualTo(uuid);

            assertThat(parser.nextToken()).isEqualTo(JsonToken.END_ARRAY);
            assertThat(parser.nextToken()).isNull();
        }
    }

    @Test
    void factoryFeaturesControlTypeHeaderAndIntegerEncoding() throws Exception {
        CBORFactory headerFactory = CBORFactory.builder()
                .enable(CBORGenerator.Feature.WRITE_TYPE_HEADER)
                .build();
        CBORMapper headerMapper = new CBORMapper(headerFactory);

        byte[] withTypeHeader = headerMapper.writeValueAsBytes(List.of(1, 24, 256));
        JsonNode decodedHeaderDocument = headerMapper.readTree(withTypeHeader);

        assertThat(Arrays.copyOf(withTypeHeader, 3)).containsExactly((byte) 0xD9, (byte) 0xD9, (byte) 0xF7);
        assertThat(headerFactory.hasFormat(new InputAccessor.Std(withTypeHeader))).isEqualTo(MatchStrength.FULL_MATCH);
        assertThat(decodedHeaderDocument).hasSize(3);
        assertThat(decodedHeaderDocument.get(0).asInt()).isEqualTo(1);
        assertThat(decodedHeaderDocument.get(1).asInt()).isEqualTo(24);
        assertThat(decodedHeaderDocument.get(2).asInt()).isEqualTo(256);

        CBORMapper minimalMapper = new CBORMapper(new CBORFactory());
        CBORMapper fixedWidthMapper = new CBORMapper(CBORFactory.builder()
                .disable(CBORGenerator.Feature.WRITE_MINIMAL_INTS)
                .build());

        byte[] minimalEncoding = minimalMapper.writeValueAsBytes(23);
        byte[] fixedWidthEncoding = fixedWidthMapper.writeValueAsBytes(23);

        assertThat(minimalEncoding).isEqualTo(new byte[] {0x17});
        assertThat(fixedWidthEncoding).isEqualTo(new byte[] {0x1A, 0x00, 0x00, 0x00, 0x17});
        assertThat(fixedWidthMapper.readValue(fixedWidthEncoding, Integer.class)).isEqualTo(23);
    }

    @Test
    void lenientUtfEncodingReplacesInvalidSurrogatePairs() throws Exception {
        String textWithInvalidSurrogate = "prefix" + '\uD83D' + "suffix";
        CBORFactory strictFactory = CBORFactory.builder()
                .disable(CBORGenerator.Feature.LENIENT_UTF_ENCODING)
                .build();

        assertThatThrownBy(() -> {
            ByteArrayOutputStream strictOutput = new ByteArrayOutputStream();
            try (JsonGenerator generator = strictFactory.createGenerator(strictOutput)) {
                generator.writeString(textWithInvalidSurrogate);
            }
        }).isInstanceOf(JsonGenerationException.class)
                .hasMessageContaining("Invalid surrogate pair");

        CBORFactory lenientFactory = CBORFactory.builder()
                .enable(CBORGenerator.Feature.LENIENT_UTF_ENCODING)
                .build();
        ByteArrayOutputStream lenientOutput = new ByteArrayOutputStream();

        try (JsonGenerator generator = lenientFactory.createGenerator(lenientOutput)) {
            generator.writeString(textWithInvalidSurrogate);
        }

        try (JsonParser parser = lenientFactory.createParser(lenientOutput.toByteArray())) {
            assertThat(parser.nextToken()).isEqualTo(JsonToken.VALUE_STRING);
            assertThat(parser.getText()).isEqualTo("prefix\uFFFDsuffix");
            assertThat(parser.nextToken()).isNull();
        }
    }

    @Test
    void factoryReportsCborCapabilitiesAndRoundTripsCopiedFactoryConfiguration() throws Exception {
        CBORFactory factory = CBORFactory.builder()
                .enable(CBORGenerator.Feature.WRITE_TYPE_HEADER)
                .disable(CBORGenerator.Feature.WRITE_MINIMAL_INTS)
                .build();
        CBORFactory copied = factory.copy();

        assertThat(factory.getFormatName()).isEqualTo(CBORFactory.FORMAT_NAME);
        assertThat(factory.canHandleBinaryNatively()).isTrue();
        assertThat(factory.canUseCharArrays()).isFalse();
        assertThat(factory.getFormatWriteFeatureType()).isEqualTo(CBORGenerator.Feature.class);
        assertThat(factory.getFormatReadFeatureType()).isEqualTo(CBORParser.Feature.class);
        assertThat(copied.isEnabled(CBORGenerator.Feature.WRITE_TYPE_HEADER)).isTrue();
        assertThat(copied.isEnabled(CBORGenerator.Feature.WRITE_MINIMAL_INTS)).isFalse();

        CBORMapper mapper = CBORMapper.builder(copied).build();
        byte[] encoded = mapper.writeValueAsBytes(Map.of("answer", 42));
        JsonNode decoded = mapper.readTree(encoded);

        assertThat(Arrays.copyOf(encoded, 3)).containsExactly((byte) 0xD9, (byte) 0xD9, (byte) 0xF7);
        assertThat(decoded.path("answer").asInt()).isEqualTo(42);
    }
}
