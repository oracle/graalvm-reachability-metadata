/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package software_amazon_awssdk.json_utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import software.amazon.awssdk.protocols.jsoncore.JsonNode;
import software.amazon.awssdk.protocols.jsoncore.JsonNodeParser;
import software.amazon.awssdk.protocols.jsoncore.JsonWriter;
import software.amazon.awssdk.thirdparty.jackson.core.JsonFactory;
import software.amazon.awssdk.thirdparty.jackson.core.JsonGenerator;

public class JsonWriterTest {
    @Test
    void writesNestedObjectsAndEscapedStringsAsUtf8() {
        String message = "caf\u00e9 \"tea\"\nsecond line\\end";
        byte[] bytes;
        try (JsonWriter writer = JsonWriter.create()) {
            writer.writeStartObject()
                    .writeFieldName("message").writeValue(message)
                    .writeFieldName("enabled").writeValue(true)
                    .writeFieldName("items").writeStartArray()
                    .writeNull()
                    .writeStartObject().writeFieldName("id").writeValue(7).writeEndObject()
                    .writeEndArray()
                    .writeEndObject();
            bytes = writer.getBytes();
        }

        assertThat(new String(bytes, StandardCharsets.UTF_8)).contains("caf\u00e9", "\\\"tea\\\"", "\\n", "\\\\end");
        JsonNode result = JsonNode.parser().parse(bytes);
        assertThat(result.field("message").orElseThrow().asString()).isEqualTo(message);
        assertThat(result.field("enabled").orElseThrow().asBoolean()).isTrue();
        JsonNode items = result.field("items").orElseThrow();
        assertThat(items.asArray()).hasSize(2);
        assertThat(items.index(0).orElseThrow().isNull()).isTrue();
        assertThat(items.index(1).orElseThrow().field("id").orElseThrow().asNumber()).isEqualTo("7");
    }

    @Test
    void writesNumericOverloadsWithoutLosingPrecision() {
        try (JsonWriter writer = JsonWriter.create()) {
            writer.writeStartArray()
                    .writeValue((short) 3)
                    .writeValue(17)
                    .writeValue(9007199254740993L)
                    .writeValue(1.25f)
                    .writeValue(-2.5d)
                    .writeValue(new BigInteger("123456789012345678901234567890"))
                    .writeValue(new BigDecimal("123.4500"))
                    .writeNumber("6.022e23")
                    .writeEndArray();

            assertThat(new String(writer.getBytes(), StandardCharsets.UTF_8)).isEqualTo(
                    "[3,17,9007199254740993,1.25,-2.5,123456789012345678901234567890,\"123.4500\",6.022e23]");
        }
    }

    @Test
    void writesRemainingBinaryBufferAndFractionalTimestamp() {
        ByteBuffer buffer = ByteBuffer.wrap(new byte[] {99, 1, 2, 3, 88});
        buffer.position(1);
        buffer.limit(4);
        try (JsonWriter writer = JsonWriter.create()) {
            writer.writeStartObject()
                    .writeFieldName("data").writeValue(buffer)
                    .writeFieldName("created").writeValue(Instant.ofEpochSecond(123, 500_000_000))
                    .writeEndObject();

            JsonNode result = JsonNodeParser.create().parse(writer.getBytes());
            assertThat(result.field("data").orElseThrow().asString()).isEqualTo("AQID");
            assertThat(new BigDecimal(result.field("created").orElseThrow().asNumber()))
                    .isEqualByComparingTo("123.5");
            assertThat(buffer.position()).isEqualTo(1);
            assertThat(buffer.limit()).isEqualTo(4);
        }
    }

    @Test
    void supportsCustomGeneratorConfiguration() {
        JsonFactory factory = new JsonFactory();
        try (JsonWriter writer = JsonWriter.builder().jsonGeneratorFactory(output -> {
            JsonGenerator generator = factory.createGenerator(output);
            generator.useDefaultPrettyPrinter();
            return generator;
        }).build()) {
            writer.writeStartObject().writeFieldName("count").writeValue(4).writeEndObject();
            byte[] bytes = writer.getBytes();
            assertThat(new String(bytes, StandardCharsets.UTF_8)).contains("\n", "\"count\"");
            assertThat(JsonNode.parser().parse(bytes).field("count").orElseThrow().asNumber()).isEqualTo("4");
        }
    }

    @Test
    void usesProvidedJsonFactory() {
        try (JsonWriter writer = JsonWriter.builder().jsonFactory(new JsonFactory()).build()) {
            writer.writeStartArray().writeValue(false).writeValue("ready").writeEndArray();
            assertThat(new String(writer.getBytes(), StandardCharsets.UTF_8)).isEqualTo("[false,\"ready\"]");
        }
    }
}
