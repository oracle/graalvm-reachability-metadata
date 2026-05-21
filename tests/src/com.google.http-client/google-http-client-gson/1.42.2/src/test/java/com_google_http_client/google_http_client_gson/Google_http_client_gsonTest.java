/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_http_client.google_http_client_gson;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonGenerator;
import com.google.api.client.json.JsonParser;
import com.google.api.client.json.JsonToken;
import com.google.api.client.json.gson.GsonFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

public class Google_http_client_gsonTest {
    @Test
    void generatedJsonCanBeParsedFromUtf8Stream() throws Exception {
        GsonFactory factory = new GsonFactory();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        JsonGenerator generator = factory.createJsonGenerator(output, StandardCharsets.UTF_8);
        try {
            generator.writeStartObject();
            generator.writeFieldName("message");
            generator.writeString("hello");
            generator.writeFieldName("active");
            generator.writeBoolean(true);
            generator.writeFieldName("count");
            generator.writeNumber(7);
            generator.writeFieldName("longValue");
            generator.writeNumber(9_007_199_254_740_993L);
            generator.writeFieldName("price");
            generator.writeNumber(new BigDecimal("12.50"));
            generator.writeFieldName("rawNumber");
            generator.writeNumber("123456789012345678901234567890");
            generator.writeFieldName("nothing");
            generator.writeNull();
            generator.writeFieldName("items");
            generator.writeStartArray();
            generator.writeString("one");
            generator.writeNumber(2.5d);
            generator.writeNumber(3.25f);
            generator.writeEndArray();
            generator.writeEndObject();
        } finally {
            generator.close();
        }

        String json = output.toString(StandardCharsets.UTF_8);
        JsonParser parser = factory.createJsonParser(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
        try {
            assertSame(factory, parser.getFactory());
            assertEquals(JsonToken.START_OBJECT, parser.nextToken());

            assertEquals(JsonToken.FIELD_NAME, parser.nextToken());
            assertEquals("message", parser.getCurrentName());
            assertEquals(JsonToken.VALUE_STRING, parser.nextToken());
            assertEquals("hello", parser.getText());

            assertEquals(JsonToken.FIELD_NAME, parser.nextToken());
            assertEquals("active", parser.getCurrentName());
            assertEquals(JsonToken.VALUE_TRUE, parser.nextToken());

            assertEquals(JsonToken.FIELD_NAME, parser.nextToken());
            assertEquals("count", parser.getCurrentName());
            assertEquals(JsonToken.VALUE_NUMBER_INT, parser.nextToken());
            assertEquals(7, parser.getIntValue());
            assertEquals(7, parser.getByteValue());
            assertEquals(7, parser.getShortValue());

            assertEquals(JsonToken.FIELD_NAME, parser.nextToken());
            assertEquals("longValue", parser.getCurrentName());
            assertEquals(JsonToken.VALUE_NUMBER_INT, parser.nextToken());
            assertEquals(9_007_199_254_740_993L, parser.getLongValue());

            assertEquals(JsonToken.FIELD_NAME, parser.nextToken());
            assertEquals("price", parser.getCurrentName());
            assertEquals(JsonToken.VALUE_NUMBER_FLOAT, parser.nextToken());
            assertEquals(new BigDecimal("12.50"), parser.getDecimalValue());

            assertEquals(JsonToken.FIELD_NAME, parser.nextToken());
            assertEquals("rawNumber", parser.getCurrentName());
            assertEquals(JsonToken.VALUE_NUMBER_INT, parser.nextToken());
            assertEquals(new BigInteger("123456789012345678901234567890"), parser.getBigIntegerValue());

            assertEquals(JsonToken.FIELD_NAME, parser.nextToken());
            assertEquals("nothing", parser.getCurrentName());
            assertEquals(JsonToken.VALUE_NULL, parser.nextToken());

            assertEquals(JsonToken.FIELD_NAME, parser.nextToken());
            assertEquals("items", parser.getCurrentName());
            assertEquals(JsonToken.START_ARRAY, parser.nextToken());
            assertSame(parser, parser.skipChildren());
            assertEquals(JsonToken.END_OBJECT, parser.nextToken());
        } finally {
            parser.close();
        }

        JsonParser defaultCharsetParser = factory.createJsonParser(
                new ByteArrayInputStream("[\"default-charset\"]".getBytes(StandardCharsets.UTF_8)));
        try {
            assertEquals(JsonToken.START_ARRAY, defaultCharsetParser.nextToken());
            assertEquals(JsonToken.VALUE_STRING, defaultCharsetParser.nextToken());
            assertEquals("default-charset", defaultCharsetParser.getText());
            assertEquals(JsonToken.END_ARRAY, defaultCharsetParser.nextToken());
        } finally {
            defaultCharsetParser.close();
        }
    }

    @Test
    void prettyPrintedWriterOutputRoundTripsThroughReaderParser() throws Exception {
        JsonFactory factory = GsonFactory.getDefaultInstance();
        assertSame(factory, GsonFactory.getDefaultInstance());

        StringWriter writer = new StringWriter();
        JsonGenerator generator = factory.createJsonGenerator(writer);
        try {
            assertSame(factory, generator.getFactory());
            generator.enablePrettyPrint();
            generator.writeStartArray();
            generator.writeStartObject();
            generator.writeFieldName("escaped");
            generator.writeString("line\nbreak");
            generator.writeFieldName("enabled");
            generator.writeBoolean(false);
            generator.writeFieldName("big");
            generator.writeNumber(new BigInteger("98765432109876543210"));
            generator.writeFieldName("missing");
            generator.writeNull();
            generator.writeEndObject();
            generator.writeEndArray();
            generator.flush();
        } finally {
            generator.close();
        }

        String json = writer.toString();
        assertTrue(json.contains("\n"));
        assertTrue(json.contains("\\n"));

        JsonParser parser = factory.createJsonParser(new StringReader(json));
        try {
            assertEquals(JsonToken.START_ARRAY, parser.nextToken());
            assertEquals(JsonToken.START_OBJECT, parser.nextToken());

            assertEquals(JsonToken.FIELD_NAME, parser.nextToken());
            assertEquals("escaped", parser.getCurrentName());
            assertEquals(JsonToken.VALUE_STRING, parser.nextToken());
            assertEquals("line\nbreak", parser.getText());

            assertEquals(JsonToken.FIELD_NAME, parser.nextToken());
            assertEquals("enabled", parser.getCurrentName());
            assertEquals(JsonToken.VALUE_FALSE, parser.nextToken());

            assertEquals(JsonToken.FIELD_NAME, parser.nextToken());
            assertEquals("big", parser.getCurrentName());
            assertEquals(JsonToken.VALUE_NUMBER_INT, parser.nextToken());
            assertEquals(new BigInteger("98765432109876543210"), parser.getBigIntegerValue());

            assertEquals(JsonToken.FIELD_NAME, parser.nextToken());
            assertEquals("missing", parser.getCurrentName());
            assertEquals(JsonToken.VALUE_NULL, parser.nextToken());
            assertEquals(JsonToken.END_OBJECT, parser.nextToken());
            assertEquals(JsonToken.END_ARRAY, parser.nextToken());
        } finally {
            parser.close();
        }
    }

    @Test
    void stringParserNavigatesNestedDocumentsAndReadsFloatingPointNumbers() throws Exception {
        GsonFactory factory = GsonFactory.getDefaultInstance();
        String document = """
                {
                  "metadata": {"requestId": "abc-123", "attempt": 2},
                  "payload": {
                    "values": [1.5, 2.25, 3.75],
                    "status": "ok"
                  },
                  "trailer": true
                }
                """;

        JsonParser parser = factory.createJsonParser(document);
        try {
            assertEquals(JsonToken.START_OBJECT, parser.nextToken());

            assertEquals(JsonToken.FIELD_NAME, parser.nextToken());
            assertEquals("metadata", parser.getCurrentName());
            assertEquals(JsonToken.START_OBJECT, parser.nextToken());
            parser.skipChildren();

            assertEquals(JsonToken.FIELD_NAME, parser.nextToken());
            assertEquals("payload", parser.getCurrentName());
            assertEquals(JsonToken.START_OBJECT, parser.nextToken());

            assertEquals(JsonToken.FIELD_NAME, parser.nextToken());
            assertEquals("values", parser.getCurrentName());
            assertEquals(JsonToken.START_ARRAY, parser.nextToken());

            assertEquals(JsonToken.VALUE_NUMBER_FLOAT, parser.nextToken());
            assertEquals(1.5d, parser.getDoubleValue(), 0.0d);
            assertEquals(JsonToken.VALUE_NUMBER_FLOAT, parser.nextToken());
            assertEquals(2.25f, parser.getFloatValue(), 0.0f);
            assertEquals(JsonToken.VALUE_NUMBER_FLOAT, parser.nextToken());
            assertEquals(new BigDecimal("3.75"), parser.getDecimalValue());
            assertEquals(JsonToken.END_ARRAY, parser.nextToken());

            assertEquals(JsonToken.FIELD_NAME, parser.nextToken());
            assertEquals("status", parser.getCurrentName());
            assertEquals(JsonToken.VALUE_STRING, parser.nextToken());
            assertEquals("ok", parser.getText());
            assertEquals(JsonToken.END_OBJECT, parser.nextToken());

            assertEquals(JsonToken.FIELD_NAME, parser.nextToken());
            assertEquals("trailer", parser.getCurrentName());
            assertEquals(JsonToken.VALUE_TRUE, parser.nextToken());
            assertEquals(JsonToken.END_OBJECT, parser.nextToken());
        } finally {
            parser.close();
        }
    }
}
