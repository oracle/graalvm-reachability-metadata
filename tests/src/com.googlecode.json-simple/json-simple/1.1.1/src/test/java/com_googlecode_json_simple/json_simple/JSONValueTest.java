/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_googlecode_json_simple.json_simple;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.json.simple.JSONAware;
import org.json.simple.JSONStreamAware;
import org.json.simple.JSONValue;
import org.json.simple.parser.ParseException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class JSONValueTest {
    @Test
    void parsesScalarValuesAndReaderInput() throws Exception {
        assertEquals(42L, JSONValue.parse("42"));
        assertEquals(-12.5, JSONValue.parseWithException("-1.25e1"));
        assertEquals(Boolean.TRUE, JSONValue.parse("true"));
        assertEquals(Boolean.FALSE, JSONValue.parseWithException("false"));
        assertNull(JSONValue.parseWithException("null"));
        assertEquals("hello\nworld", JSONValue.parseWithException("\"hello\\nworld\""));
        try (StringReader reader = new StringReader("[1,true,\"ready\"]")) {
            assertEquals(List.of(1L, true, "ready"), JSONValue.parseWithException(reader));
        }
        try (StringReader reader = new StringReader("{\"count\":3}")) {
            assertEquals(Map.of("count", 3L), JSONValue.parse(reader));
        }
    }

    @Test
    void exposesStrictAndLenientParsingForInvalidInput() {
        assertNull(JSONValue.parse("[1,?]"));
        assertThrows(ParseException.class, () -> JSONValue.parseWithException("[1,?]"));
    }

    @Test
    void encodesPlainMapsAndListsThroughBothOutputApis() throws Exception {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("name", "tea\n\"cup\"");
        document.put("values", Arrays.asList(5L, 2.5, true, null));
        String expected = """
                {"name":"tea\\n\\\"cup\\\"","values":[5,2.5,true,null]}""";
        StringWriter writer = new StringWriter();

        JSONValue.writeJSONString(document, writer);

        assertEquals(expected, JSONValue.toJSONString(document));
        assertEquals(expected, writer.toString());
        assertEquals(document, JSONValue.parseWithException(writer.toString()));
    }

    @Test
    void writesNullForNonFiniteNumbers() throws Exception {
        List<Object> values = Arrays.asList(null, Double.NaN, Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY);
        for (Object value : values) {
            StringWriter writer = new StringWriter();
            JSONValue.writeJSONString(value, writer);
            assertEquals("null", JSONValue.toJSONString(value));
            assertEquals("null", writer.toString());
        }
        StringWriter writer = new StringWriter();
        JSONValue.writeJSONString(1.25f, writer);
        assertEquals("1.25", writer.toString());
        assertEquals("1.25", JSONValue.toJSONString(1.25f));
    }

    @Test
    void escapesSpecialCharactersAndRoundTripsUnicode() throws Exception {
        String value = "\"\\/\b\f\n\r\t" + (char) 1 + "\u00e9\u20ac";
        String expected = "\\\"\\\\\\/\\b\\f\\n\\r\\t\\u0001\u00e9\\u20AC";
        assertEquals(expected, JSONValue.escape(value));
        assertEquals(value, JSONValue.parseWithException(JSONValue.toJSONString(value)));
        assertNull(JSONValue.escape(null));
    }

    @Test
    void usesConsumerProvidedJsonAwareAndStreamAwareEncoders() throws Exception {
        JSONAware aware = () -> "{\"kind\":\"custom\"}";
        JSONStreamAware streamAware = writer -> writer.write("[\"streamed\",7]");
        StringWriter awareWriter = new StringWriter();
        StringWriter streamWriter = new StringWriter();

        JSONValue.writeJSONString(aware, awareWriter);
        JSONValue.writeJSONString(streamAware, streamWriter);

        assertEquals(Map.of("kind", "custom"), JSONValue.parseWithException(JSONValue.toJSONString(aware)));
        assertEquals(aware.toJSONString(), awareWriter.toString());
        assertEquals(List.of("streamed", 7L), JSONValue.parseWithException(streamWriter.toString()));
    }
}
