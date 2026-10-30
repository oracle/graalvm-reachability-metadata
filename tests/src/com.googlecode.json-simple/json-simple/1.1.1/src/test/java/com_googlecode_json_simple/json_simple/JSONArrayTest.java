/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_googlecode_json_simple.json_simple;

import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.json.simple.JSONArray;
import org.json.simple.JSONValue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JSONArrayTest {
    @Test
    void roundTripsMixedAndNestedElements() throws Exception {
        JSONArray array = new JSONArray();
        array.add("first");
        array.add(4L);
        array.add(false);
        array.add(null);
        array.add(Map.of("id", 9L));
        array.add(List.of("nested", 2L));
        String expected = """
                ["first",4,false,null,{"id":9},["nested",2]]""";
        StringWriter writer = new StringWriter();

        array.writeJSONString(writer);

        assertEquals(expected, array.toJSONString());
        assertEquals(expected, array.toString());
        assertEquals(expected, writer.toString());
        assertEquals(array, JSONValue.parseWithException(writer.toString()));
    }

    @Test
    void encodesPlainListIncludingNullAndEscapedStrings() throws Exception {
        List<Object> values = Arrays.asList("a\tb", null, 3L, true);
        String expected = """
                ["a\\tb",null,3,true]""";
        StringWriter writer = new StringWriter();

        JSONArray.writeJSONString(values, writer);

        assertEquals(expected, JSONArray.toJSONString(values));
        assertEquals(expected, writer.toString());
        assertEquals(values, JSONValue.parseWithException(writer.toString()));
    }

    @Test
    void encodesEmptyAndNullLists() throws Exception {
        assertEquals("[]", new JSONArray().toJSONString());
        assertEquals("null", JSONArray.toJSONString(null));
        StringWriter writer = new StringWriter();
        JSONArray.writeJSONString(null, writer);
        assertEquals("null", writer.toString());
    }
}
