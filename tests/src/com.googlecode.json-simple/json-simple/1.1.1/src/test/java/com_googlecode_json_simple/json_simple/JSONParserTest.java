/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_googlecode_json_simple.json_simple;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.ContainerFactory;
import org.json.simple.parser.ContentHandler;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JSONParserTest {
    @Test
    void parsesNestedContainersAndEscapedStrings() throws Exception {
        String json = """
                {"items":[{"id":12},[false,null]],"text":"caf\\u00e9\\nline","ratio":6.25e-1}
                """;
        JSONParser parser = new JSONParser();
        JSONObject document;
        try (StringReader reader = new StringReader(json)) {
            document = assertInstanceOf(JSONObject.class, parser.parse(reader));
        }

        JSONArray items = assertInstanceOf(JSONArray.class, document.get("items"));
        JSONObject item = assertInstanceOf(JSONObject.class, items.get(0));
        JSONArray nested = assertInstanceOf(JSONArray.class, items.get(1));
        assertEquals(12L, item.get("id"));
        assertEquals(Boolean.FALSE, nested.get(0));
        assertEquals(null, nested.get(1));
        assertEquals("caf\u00e9\nline", document.get("text"));
        assertEquals(0.625, document.get("ratio"));
    }

    @Test
    void buildsConsumerSelectedContainersAtEveryNestingLevel() throws Exception {
        ContainerFactory factory = new ContainerFactory() {
            @Override
            public Map<String, Object> createObjectContainer() {
                return new LinkedHashMap<>();
            }

            @Override
            public List<Object> creatArrayContainer() {
                return new LinkedList<>();
            }
        };
        JSONParser parser = new JSONParser();
        Map<?, ?> document = assertInstanceOf(LinkedHashMap.class,
                parser.parse("{\"first\":[{\"nested\":[1,2]}],\"second\":true}", factory));

        assertEquals(List.of("first", "second"), new ArrayList<>(document.keySet()));
        List<?> first = assertInstanceOf(LinkedList.class, document.get("first"));
        Map<?, ?> child = assertInstanceOf(LinkedHashMap.class, first.get(0));
        List<?> nested = assertInstanceOf(LinkedList.class, child.get("nested"));
        assertEquals(List.of(1L, 2L), nested);
        assertEquals(Boolean.TRUE, document.get("second"));
    }

    @Test
    void fallsBackToDefaultContainersWhenFactoryReturnsNull() throws Exception {
        ContainerFactory factory = new ContainerFactory() {
            @Override
            public Map<?, ?> createObjectContainer() {
                return null;
            }

            @Override
            public List<?> creatArrayContainer() {
                return null;
            }
        };
        JSONObject document = assertInstanceOf(JSONObject.class,
                new JSONParser().parse("{\"items\":[1]}", factory));
        JSONArray items = assertInstanceOf(JSONArray.class, document.get("items"));
        assertEquals(List.of(1L), items);
    }

    @Test
    void reportsInvalidInputAndCanParseAnotherDocument() throws Exception {
        JSONParser parser = new JSONParser();
        ParseException invalidCharacter = assertThrows(ParseException.class, () -> parser.parse("[1,@]"));
        assertEquals(ParseException.ERROR_UNEXPECTED_CHAR, invalidCharacter.getErrorType());
        assertEquals(3, invalidCharacter.getPosition());
        assertEquals('@', invalidCharacter.getUnexpectedObject());
        assertTrue(invalidCharacter.toString().contains("position 3"));

        ParseException incomplete = assertThrows(ParseException.class, () -> parser.parse("{\"a\":"));
        assertEquals(ParseException.ERROR_UNEXPECTED_TOKEN, incomplete.getErrorType());
        assertThrows(ParseException.class, () -> parser.parse("true false"));
        assertEquals(Map.of("ready", true), parser.parse("{\"ready\":true}"));
        assertEquals(List.of(), parser.parse("[]"));
    }

    @Test
    void streamsNestedDocumentAsOrderedCallbacks() throws Exception {
        RecordingHandler handler = new RecordingHandler(false);
        JSONParser parser = new JSONParser();
        try (StringReader reader = new StringReader("{\"items\":[1,{\"ok\":true}],\"other\":null}")) {
            parser.parse(reader, handler);
        }

        assertEquals(List.of("startJSON", "startObject", "key:items", "startArray", "value:1",
                "startObject", "key:ok", "value:true", "endEntry", "endObject", "endArray",
                "endEntry", "key:other", "value:null", "endEntry", "endObject", "endJSON"), handler.events);
    }

    @Test
    void pausesAndResumesStreamingWithoutRepeatingValues() throws Exception {
        RecordingHandler handler = new RecordingHandler(true);
        JSONParser parser = new JSONParser();
        try (StringReader reader = new StringReader("[10,20,30]")) {
            parser.parse(reader, handler, false);
            assertEquals(List.of("startJSON", "startArray", "value:10"), handler.events);

            parser.parse(reader, handler, true);
        }

        assertEquals(List.of("startJSON", "startArray", "value:10", "value:20", "value:30",
                "endArray", "endJSON"), handler.events);
    }

    private static class RecordingHandler implements ContentHandler {
        private final List<String> events = new ArrayList<>();
        private boolean pauseOnNextPrimitive;

        RecordingHandler(boolean pauseOnNextPrimitive) {
            this.pauseOnNextPrimitive = pauseOnNextPrimitive;
        }

        @Override
        public void startJSON() {
            events.add("startJSON");
        }

        @Override
        public void endJSON() {
            events.add("endJSON");
        }

        @Override
        public boolean startObject() {
            events.add("startObject");
            return true;
        }

        @Override
        public boolean endObject() {
            events.add("endObject");
            return true;
        }

        @Override
        public boolean startObjectEntry(String key) {
            events.add("key:" + key);
            return true;
        }

        @Override
        public boolean endObjectEntry() {
            events.add("endEntry");
            return true;
        }

        @Override
        public boolean startArray() {
            events.add("startArray");
            return true;
        }

        @Override
        public boolean endArray() {
            events.add("endArray");
            return true;
        }

        @Override
        public boolean primitive(Object value) {
            events.add("value:" + value);
            boolean continueParsing = !pauseOnNextPrimitive;
            pauseOnNextPrimitive = false;
            return continueParsing;
        }
    }
}
