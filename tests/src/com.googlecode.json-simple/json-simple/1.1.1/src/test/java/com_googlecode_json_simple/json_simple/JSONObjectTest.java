/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_googlecode_json_simple.json_simple;

import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class JSONObjectTest {
    @Test
    void roundTripsNestedObjectThroughStringAndWriter() throws Exception {
        JSONObject document = new JSONObject();
        document.put("title", "notebook");
        document.put("tags", List.of("work", "personal"));
        document.put("settings", Map.of("enabled", true));
        document.put("optional", null);
        StringWriter writer = new StringWriter();

        document.writeJSONString(writer);

        assertEquals(document, JSONValue.parseWithException(document.toJSONString()));
        assertEquals(document, JSONValue.parseWithException(document.toString()));
        assertEquals(document, JSONValue.parseWithException(writer.toString()));
    }

    @Test
    void copiesMapWithoutSharingTopLevelEntries() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("name", "original");
        JSONObject document = new JSONObject(source);

        source.put("name", "changed");
        document.put("onlyInJson", true);

        assertEquals("original", document.get("name"));
        assertFalse(source.containsKey("onlyInJson"));
    }

    @Test
    void encodesOrderedMapAndEscapesKeys() throws Exception {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("quoted\"key", 8L);
        values.put("line\nkey", false);
        String expected = """
                {"quoted\\\"key":8,"line\\nkey":false}""";
        StringWriter writer = new StringWriter();

        JSONObject.writeJSONString(values, writer);

        assertEquals(expected, JSONObject.toJSONString(values));
        assertEquals(expected, writer.toString());
        assertEquals(values, JSONValue.parseWithException(writer.toString()));
        assertEquals("\"line\\nkey\":9", JSONObject.toString("line\nkey", 9L));
        assertEquals("line\\nkey", JSONObject.escape("line\nkey"));
    }

    @Test
    void encodesEmptyAndNullMaps() throws Exception {
        assertEquals("{}", new JSONObject().toJSONString());
        assertEquals("null", JSONObject.toJSONString(null));
        StringWriter writer = new StringWriter();
        JSONObject.writeJSONString(null, writer);
        assertEquals("null", writer.toString());
    }
}
