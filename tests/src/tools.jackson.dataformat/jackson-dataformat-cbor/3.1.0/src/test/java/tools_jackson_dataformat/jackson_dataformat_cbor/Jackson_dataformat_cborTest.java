/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tools_jackson_dataformat.jackson_dataformat_cbor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.dataformat.cbor.CBORFactory;
import tools.jackson.dataformat.cbor.CBORGenerator;
import tools.jackson.dataformat.cbor.CBORMapper;
import tools.jackson.dataformat.cbor.CBORParser;
import tools.jackson.dataformat.cbor.CBORReadFeature;
import tools.jackson.dataformat.cbor.CBORSimpleValue;
import tools.jackson.dataformat.cbor.CBORWriteFeature;

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

        assertThat(tree.path("service").asString()).isEqualTo("telemetry");
        assertThat(tree.path("version").asInt()).isEqualTo(3);
        assertThat(tree.path("labels")).hasSize(3);
        assertThat(tree.path("labels").get(2).asString()).isEqualTo("native-image");
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

        assertThat(decoded.path("name").asString()).isEqualTo("metrics-batch");
        assertThat(decoded.path("count").asInt()).isEqualTo(2);
        assertThat(decoded.path("complete").asBoolean()).isFalse();
        assertThat(decoded.path("checksum").binaryValue()).isEqualTo(new byte[] {(byte) 0xCA, (byte) 0xFE, 0x42});
        assertThat(decoded.path("samples")).hasSize(2);
        assertThat(decoded.path("samples").get(0).path("host").asString()).isEqualTo("alpha");
        assertThat(decoded.path("samples").get(0).path("value").asDouble()).isEqualTo(1.25d);
        assertThat(decoded.path("samples").get(1).path("host").asString()).isEqualTo("beta");
        assertThat(decoded.path("samples").get(1).path("value").asDouble()).isEqualTo(2.5d);
    }

    @Test
    void streamingGeneratorAndParserExposeExpectedTokenSequenceAndTypedValues() throws Exception {
        CBORFactory factory = new CBORFactory();
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (CBORGenerator generator = (CBORGenerator) factory.createGenerator(output)) {
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

        try (JsonParser parser = factory.createParser(output.toByteArray())) {
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
    void binaryValuesCanBeWrittenFromAndReadToStreams() throws Exception {
        CBORFactory factory = new CBORFactory();
        byte[] payload = new byte[128];
        for (int index = 0; index < payload.length; index++) {
            payload[index] = (byte) (index * 3 + 1);
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (JsonGenerator generator = factory.createGenerator(output)) {
            generator.writeStartObject();
            generator.writeStringProperty("name", "firmware");
            generator.writeName("bytes");
            int bytesWritten = generator.writeBinary(new ByteArrayInputStream(payload), payload.length);
            generator.writeEndObject();

            assertThat(bytesWritten).isEqualTo(payload.length);
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
    void streamingGeneratorWritesPrimitiveArraysInBulkWithOffsets() throws Exception {
        CBORFactory factory = new CBORFactory();
        int[] counts = new int[] {99, 3, 5, 8, 99};
        long[] timestamps = new long[] {1_700_000_001L, 1_700_000_002L, 1_700_000_003L};
        double[] ratios = new double[] {-1.0d, 0.25d, 0.5d, 1.0d};
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (JsonGenerator generator = factory.createGenerator(output)) {
            generator.writeStartObject();
            generator.writeName("counts");
            generator.writeArray(counts, 1, 3);
            generator.writeName("timestamps");
            generator.writeArray(timestamps, 0, 2);
            generator.writeName("ratios");
            generator.writeArray(ratios, 1, 2);
            generator.writeEndObject();
        }

        try (JsonParser parser = factory.createParser(output.toByteArray())) {
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
    void cborTagsArePreservedForTaggedScalarValues() throws Exception {
        CBORFactory factory = new CBORFactory();
        String uuid = "f81d4fae-7dec-11d0-a765-00a0c91e6bf6";
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (CBORGenerator generator = (CBORGenerator) factory.createGenerator(output)) {
            generator.writeStartArray();
            generator.writeTag(1);
            generator.writeNumber(1_700_000_000L);
            generator.writeTag(37);
            generator.writeString(uuid);
            generator.writeEndArray();
        }

        try (CBORParser parser = (CBORParser) factory.createParser(output.toByteArray())) {
            assertThat(parser.nextToken()).isEqualTo(JsonToken.START_ARRAY);

            assertThat(parser.nextToken()).isEqualTo(JsonToken.VALUE_NUMBER_INT);
            assertThat(parser.getCurrentTag()).isEqualTo(1);
            assertThat(parser.getLongValue()).isEqualTo(1_700_000_000L);

            assertThat(parser.nextToken()).isEqualTo(JsonToken.VALUE_STRING);
            assertThat(parser.getCurrentTag()).isEqualTo(37);
            assertThat(parser.getString()).isEqualTo(uuid);

            assertThat(parser.nextToken()).isEqualTo(JsonToken.END_ARRAY);
            assertThat(parser.nextToken()).isNull();
        }
    }

    @Test
    void factoryFeaturesControlTypeHeaderAndIntegerEncoding() throws Exception {
        CBORFactory headerFactory = CBORFactory.builder()
                .enable(CBORWriteFeature.WRITE_TYPE_HEADER)
                .build();
        CBORMapper headerMapper = new CBORMapper(headerFactory);

        byte[] withTypeHeader = headerMapper.writeValueAsBytes(List.of(1, 24, 256));
        JsonNode decodedHeaderDocument = headerMapper.readTree(withTypeHeader);

        assertThat(Arrays.copyOf(withTypeHeader, 3)).containsExactly((byte) 0xD9, (byte) 0xD9, (byte) 0xF7);
        assertThat(decodedHeaderDocument).hasSize(3);
        assertThat(decodedHeaderDocument.get(0).asInt()).isEqualTo(1);
        assertThat(decodedHeaderDocument.get(1).asInt()).isEqualTo(24);
        assertThat(decodedHeaderDocument.get(2).asInt()).isEqualTo(256);

        CBORMapper minimalMapper = new CBORMapper(new CBORFactory());
        CBORMapper fixedWidthMapper = new CBORMapper(CBORFactory.builder()
                .disable(CBORWriteFeature.WRITE_MINIMAL_INTS)
                .build());

        byte[] minimalEncoding = minimalMapper.writeValueAsBytes(23);
        byte[] fixedWidthEncoding = fixedWidthMapper.writeValueAsBytes(23);

        assertThat(minimalEncoding).containsExactly((byte) 0x17);
        assertThat(fixedWidthEncoding).containsExactly((byte) 0x1A, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x17);
        assertThat(fixedWidthMapper.readValue(fixedWidthEncoding, Integer.class)).isEqualTo(23);
    }

    @Test
    void simpleAndUndefinedValuesCanBeReadAsEmbeddedObjectsOrCoercedNumbersAndNulls() throws Exception {
        byte[] undefined = new byte[] {(byte) 0xF7};
        byte[] simpleValue = new byte[] {(byte) 0xE9};

        try (JsonParser parser = new CBORFactory().createParser(undefined)) {
            assertThat(parser.nextToken()).isEqualTo(JsonToken.VALUE_EMBEDDED_OBJECT);
            assertThat(parser.getEmbeddedObject()).isNull();
        }
        try (JsonParser parser = CBORFactory.builder()
                .disable(CBORReadFeature.READ_UNDEFINED_AS_EMBEDDED_OBJECT)
                .build()
                .createParser(undefined)) {
            assertThat(parser.nextToken()).isEqualTo(JsonToken.VALUE_NULL);
        }

        try (JsonParser parser = new CBORFactory().createParser(simpleValue)) {
            assertThat(parser.nextToken()).isEqualTo(JsonToken.VALUE_EMBEDDED_OBJECT);
            assertThat(parser.getEmbeddedObject()).isEqualTo(new CBORSimpleValue(9));
        }
        try (JsonParser parser = CBORFactory.builder()
                .disable(CBORReadFeature.READ_SIMPLE_VALUE_AS_EMBEDDED_OBJECT)
                .build()
                .createParser(simpleValue)) {
            assertThat(parser.nextToken()).isEqualTo(JsonToken.VALUE_NUMBER_INT);
            assertThat(parser.getIntValue()).isEqualTo(9);
        }
    }

    @Test
    void lenientUtfEncodingReplacesInvalidSurrogatePairs() throws Exception {
        String textWithInvalidSurrogate = "prefix" + '\uD83D' + "suffix";
        CBORFactory strictFactory = CBORFactory.builder()
                .disable(CBORWriteFeature.LENIENT_UTF_ENCODING)
                .build();

        assertThatThrownBy(() -> {
            ByteArrayOutputStream strictOutput = new ByteArrayOutputStream();
            try (JsonGenerator generator = strictFactory.createGenerator(strictOutput)) {
                generator.writeString(textWithInvalidSurrogate);
            }
        }).isInstanceOf(JacksonException.class)
                .hasMessageContaining("Invalid surrogate pair");

        CBORFactory lenientFactory = CBORFactory.builder()
                .enable(CBORWriteFeature.LENIENT_UTF_ENCODING)
                .build();
        ByteArrayOutputStream lenientOutput = new ByteArrayOutputStream();

        try (JsonGenerator generator = lenientFactory.createGenerator(lenientOutput)) {
            generator.writeString(textWithInvalidSurrogate);
        }

        try (JsonParser parser = lenientFactory.createParser(lenientOutput.toByteArray())) {
            assertThat(parser.nextToken()).isEqualTo(JsonToken.VALUE_STRING);
            assertThat(parser.getString()).isEqualTo("prefix\uFFFDsuffix");
            assertThat(parser.nextToken()).isNull();
        }
    }

    @Test
    void stringReferencesAndMinimalDoubleEncodingPreserveLogicalValues() throws Exception {
        CBORFactory stringReferenceFactory = CBORFactory.builder()
                .enable(CBORWriteFeature.STRINGREF)
                .build();
        List<String> repeatedValues = List.of(
                "a-string-long-enough-to-enter-the-cbor-reference-table",
                "a-string-long-enough-to-enter-the-cbor-reference-table",
                "short",
                "a-string-long-enough-to-enter-the-cbor-reference-table");
        ByteArrayOutputStream stringReferenceOutput = new ByteArrayOutputStream();

        try (CBORGenerator generator = (CBORGenerator) stringReferenceFactory.createGenerator(stringReferenceOutput)) {
            generator.writeTag(256);
            generator.writeStartArray();
            for (String value : repeatedValues) {
                generator.writeString(value);
            }
            generator.writeEndArray();
        }

        try (JsonParser parser = stringReferenceFactory.createParser(stringReferenceOutput.toByteArray())) {
            assertThat(parser.nextToken()).isEqualTo(JsonToken.START_ARRAY);
            assertThat(parser.nextStringValue()).isEqualTo(repeatedValues.get(0));
            assertThat(parser.nextStringValue()).isEqualTo(repeatedValues.get(1));
            assertThat(parser.nextStringValue()).isEqualTo("short");
            assertThat(parser.nextStringValue()).isEqualTo(repeatedValues.get(3));
            assertThat(parser.nextToken()).isEqualTo(JsonToken.END_ARRAY);
            assertThat(parser.nextToken()).isNull();
        }

        CBORMapper minimalDoubleMapper = CBORMapper.builder()
                .enable(CBORWriteFeature.WRITE_MINIMAL_DOUBLES)
                .build();
        byte[] encodedMinimalDouble = minimalDoubleMapper.writeValueAsBytes(1.5d);

        assertThat(encodedMinimalDouble).containsExactly((byte) 0xFA, (byte) 0x3F, (byte) 0xC0, (byte) 0x00,
                (byte) 0x00);
        assertThat(minimalDoubleMapper.readValue(encodedMinimalDouble, Double.class)).isEqualTo(1.5d);
    }

    @Test
    void negativeBigIntegerEncodingCanBeReadWithStandardCborSemantics() throws Exception {
        CBORMapper mapper = CBORMapper.builder()
                .enable(CBORWriteFeature.ENCODE_USING_STANDARD_NEGATIVE_BIGINT_ENCODING)
                .enable(CBORReadFeature.DECODE_USING_STANDARD_NEGATIVE_BIGINT_ENCODING)
                .build();
        BigInteger value = new BigInteger("-184467440737095516170");

        byte[] encoded = mapper.writeValueAsBytes(value);
        BigInteger decoded = mapper.readValue(encoded, BigInteger.class);

        assertThat(decoded).isEqualTo(value);
    }

    @Test
    void factoryReportsCborCapabilitiesAndCopiedConfiguration() throws Exception {
        CBORFactory factory = CBORFactory.builder()
                .enable(CBORWriteFeature.WRITE_TYPE_HEADER)
                .disable(CBORWriteFeature.WRITE_MINIMAL_INTS)
                .enable(CBORReadFeature.READ_SIMPLE_VALUE_AS_EMBEDDED_OBJECT)
                .build();
        CBORFactory copied = factory.copy();
        CBORFactory snapshotted = (CBORFactory) factory.snapshot();

        assertThat(factory.getFormatName()).isEqualTo(CBORFactory.FORMAT_NAME);
        assertThat(factory.canHandleBinaryNatively()).isTrue();
        assertThat(factory.canParseAsync()).isFalse();
        assertThat(factory.getFormatWriteFeatureType()).isEqualTo(CBORWriteFeature.class);
        assertThat(factory.getFormatReadFeatureType()).isEqualTo(CBORReadFeature.class);
        assertThat(copied.isEnabled(CBORWriteFeature.WRITE_TYPE_HEADER)).isTrue();
        assertThat(copied.isEnabled(CBORWriteFeature.WRITE_MINIMAL_INTS)).isFalse();
        assertThat(snapshotted.isEnabled(CBORReadFeature.READ_SIMPLE_VALUE_AS_EMBEDDED_OBJECT)).isTrue();

        CBORMapper mapper = CBORMapper.builder(copied).build();
        byte[] encoded = mapper.writeValueAsBytes(Map.of("answer", 42));
        JsonNode decoded = mapper.readTree(encoded);

        assertThat(Arrays.copyOf(encoded, 3)).containsExactly((byte) 0xD9, (byte) 0xD9, (byte) 0xF7);
        assertThat(decoded.path("answer").asInt()).isEqualTo(42);
    }

    @Test
    void parserReadsIndefiniteLengthTextAndBinaryChunksAsSingleValues() throws Exception {
        byte[] chunkedDocument = new byte[] {
                (byte) 0xA2,
                (byte) 0x63, (byte) 'm', (byte) 's', (byte) 'g',
                (byte) 0x7F,
                (byte) 0x65, (byte) 'h', (byte) 'e', (byte) 'l', (byte) 'l', (byte) 'o',
                (byte) 0x61, (byte) ' ',
                (byte) 0x65, (byte) 'w', (byte) 'o', (byte) 'r', (byte) 'l', (byte) 'd',
                (byte) 0xFF,
                (byte) 0x65, (byte) 'b', (byte) 'y', (byte) 't', (byte) 'e', (byte) 's',
                (byte) 0x5F,
                (byte) 0x43, (byte) 0x01, (byte) 0x02, (byte) 0x03,
                (byte) 0x42, (byte) 0x04, (byte) 0x05,
                (byte) 0xFF
        };

        try (JsonParser parser = new CBORFactory().createParser(chunkedDocument)) {
            assertThat(parser.nextToken()).isEqualTo(JsonToken.START_OBJECT);

            assertThat(parser.nextName()).isEqualTo("msg");
            assertThat(parser.nextToken()).isEqualTo(JsonToken.VALUE_STRING);
            assertThat(parser.getString()).isEqualTo("hello world");

            assertThat(parser.nextName()).isEqualTo("bytes");
            assertThat(parser.nextToken()).isEqualTo(JsonToken.VALUE_EMBEDDED_OBJECT);
            assertThat(parser.getBinaryValue()).containsExactly((byte) 0x01, (byte) 0x02, (byte) 0x03,
                    (byte) 0x04, (byte) 0x05);

            assertThat(parser.nextToken()).isEqualTo(JsonToken.END_OBJECT);
            assertThat(parser.nextToken()).isNull();
        }
    }
}
