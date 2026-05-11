/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy_json;

import groovy.json.JsonBuilder;
import groovy.json.JsonException;
import groovy.json.JsonGenerator;
import groovy.json.JsonLexer;
import groovy.json.JsonOutput;
import groovy.json.JsonParserType;
import groovy.json.JsonSlurper;
import groovy.json.JsonSlurperClassic;
import groovy.json.JsonToken;
import groovy.json.JsonTokenType;
import groovy.json.StreamingJsonBuilder;
import groovy.util.Expando;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public final class Groovy_jsonTest {
    @Test
    void parsesStrictJsonWithEachSlurperParserType() {
        String json = """
                {
                    "name":"Groovy",
                    "numbers":[1,2.5,-3],
                    "enabled":true,
                    "missing":null,
                    "nested":{"escaped":"line\\nbreak","unicode":"\u2615"}
                }
                """;

        for (JsonParserType parserType : JsonParserType.values()) {
            JsonSlurper slurper = new JsonSlurper()
                    .setType(parserType)
                    .setChop(true)
                    .setLazyChop(false)
                    .setCheckDates(false);
            Map<String, Object> parsed = asMap(slurper.parseText(json));
            Map<String, Object> nested = asMap(parsed.get("nested"));
            List<?> numbers = asList(parsed.get("numbers"));

            assertThat(slurper.getType()).isEqualTo(parserType);
            assertThat(slurper.isChop()).isTrue();
            assertThat(slurper.isLazyChop()).isFalse();
            assertThat(slurper.isCheckDates()).isFalse();
            assertThat(parsed.get("name")).isEqualTo("Groovy");
            assertThat(((Number) numbers.get(0)).intValue()).isEqualTo(1);
            assertThat(numbers.get(1).toString()).isEqualTo("2.5");
            assertThat(((Number) numbers.get(2)).intValue()).isEqualTo(-3);
            assertThat(parsed.get("enabled")).isEqualTo(true);
            assertThat(parsed.get("missing")).isNull();
            assertThat(nested.get("escaped")).isEqualTo("line\nbreak");
            assertThat(nested.get("unicode")).isEqualTo("\u2615");
        }
    }

    @Test
    void laxSlurperParsesRelaxedJsonSyntax() {
        String relaxedJson = """
                // LAX mode accepts comments, unquoted object keys, and single-quoted strings.
                {
                    name: 'Groovy',
                    enabled: true,
                    nested: {
                        description: 'relaxed syntax',
                        count: 3
                    },
                    tags: [
                        'json',
                        'lax'
                    ]
                }
                """;

        Map<String, Object> parsed = asMap(new JsonSlurper()
                .setType(JsonParserType.LAX)
                .parseText(relaxedJson));

        assertThat(parsed.get("name")).isEqualTo("Groovy");
        assertThat(parsed.get("enabled")).isEqualTo(true);
        assertThat(asMap(parsed.get("nested")).get("description")).isEqualTo("relaxed syntax");
        assertThat(((Number) asMap(parsed.get("nested")).get("count")).intValue()).isEqualTo(3);
        assertThat(asList(parsed.get("tags"))).containsExactly("json", "lax");
    }

    @Test
    void parsesJsonFromReadersStreamsArraysFilesPathsAndUrls() throws Exception {
        String json = "{\"greeting\":\"h\u00e9llo\",\"values\":[1,2,3],\"nested\":{\"ok\":true}}";
        JsonSlurper slurper = new JsonSlurper();
        Path tempFile = Files.createTempFile("groovy-json-", ".json");

        try {
            Files.writeString(tempFile, json, StandardCharsets.UTF_8);

            assertParsedDocument(slurper.parse(new StringReader(json)));
            assertParsedDocument(slurper.parse(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))));
            assertParsedDocument(slurper.parse(
                    new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), "UTF-8"));
            assertParsedDocument(slurper.parse(json.getBytes(StandardCharsets.UTF_8)));
            assertParsedDocument(slurper.parse(json.getBytes(StandardCharsets.UTF_8), "UTF-8"));
            assertParsedDocument(slurper.parse(json.toCharArray()));
            assertParsedDocument(slurper.parse(tempFile));
            assertParsedDocument(slurper.parse(tempFile, "UTF-8"));
            assertParsedDocument(slurper.parse(tempFile.toFile()));
            assertParsedDocument(slurper.parse(tempFile.toFile(), "UTF-8"));
            assertParsedDocument(slurper.parse(tempFile.toUri().toURL()));

            Map<String, Object> urlOptions = new LinkedHashMap<>();
            urlOptions.put("useCaches", false);
            assertParsedDocument(slurper.parse(tempFile.toUri().toURL(), urlOptions, "UTF-8"));
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    void serializesCommonJsonOutputTypesAndRejectsInvalidJsonNumbers() throws Exception {
        UUID uuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        URL url = URI.create("file:/groovy-lang/json.html").toURL();
        Expando expando = new Expando();
        expando.setProperty("name", "dynamic");
        expando.setProperty("active", true);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("text", "quote \" slash \\ newline\n unicode \u2615");
        payload.put("truth", true);
        payload.put("count", 7);
        payload.put("decimal", 2.75D);
        payload.put("uuid", uuid);
        payload.put("url", url);
        payload.put("raw", JsonOutput.unescaped("{\"trusted\":true}"));
        payload.put("expando", expando);
        payload.put("state", Thread.State.RUNNABLE);
        payload.put("intArray", new int[]{1, 2, 3});
        payload.put("charArray", new char[]{'a', '\n'});
        payload.put("enumeration", Collections.enumeration(List.of("first", "second")));

        String json = JsonOutput.toJson(payload);
        Map<String, Object> parsed = asMap(new JsonSlurper().parseText(json));

        assertThat(parsed.get("text")).isEqualTo("quote \" slash \\ newline\n unicode \u2615");
        assertThat(parsed.get("truth")).isEqualTo(true);
        assertThat(((Number) parsed.get("count")).intValue()).isEqualTo(7);
        assertThat(parsed.get("decimal").toString()).isEqualTo("2.75");
        assertThat(parsed.get("uuid")).isEqualTo(uuid.toString());
        assertThat(parsed.get("url")).isEqualTo(url.toString());
        assertThat(asMap(parsed.get("raw")).get("trusted")).isEqualTo(true);
        assertThat(asMap(parsed.get("expando")).get("name")).isEqualTo("dynamic");
        assertThat(asMap(parsed.get("expando")).get("active")).isEqualTo(true);
        assertThat(parsed.get("state")).isEqualTo("RUNNABLE");
        assertThat(asList(parsed.get("intArray"))).extracting(Object::toString).containsExactly("1", "2", "3");
        assertThat(asList(parsed.get("charArray"))).containsExactly("a", "\n");
        assertThat(asList(parsed.get("enumeration"))).containsExactly("first", "second");
        assertThat(JsonOutput.toJson((Object) null)).isEqualTo("null");
        assertThat(JsonOutput.toJson(Character.valueOf('x'))).isEqualTo("\"x\"");

        assertThatThrownBy(() -> JsonOutput.toJson(Double.NaN))
                .isInstanceOf(JsonException.class)
                .hasMessageContaining("NaN");

        Map<Object, Object> nullKeyMap = new LinkedHashMap<>();
        nullKeyMap.put(null, "value");
        assertThatThrownBy(() -> JsonOutput.toJson(nullKeyMap))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null keys");
    }

    @Test
    void customJsonGeneratorHonorsConvertersExclusionsDatesAndUnicode() throws Exception {
        UUID uuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        JsonGenerator generator = new JsonGenerator.Options()
                .excludeNulls()
                .disableUnicodeEscaping()
                .dateFormat("yyyy-MM-dd", Locale.US)
                .timezone("GMT")
                .excludeFieldsByName("secret")
                .excludeFieldsByType(URL.class)
                .addConverter(new JsonGenerator.Converter() {
                    @Override
                    public boolean handles(Class<?> type) {
                        return UUID.class.isAssignableFrom(type);
                    }

                    @Override
                    public Object convert(Object value, String key) {
                        return key + ":" + ((UUID) value).toString().substring(0, 8);
                    }
                })
                .build();

        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("skip", null);
        nested.put("ok", 1);

        List<Object> list = new ArrayList<>();
        list.add(null);
        list.add(URI.create("file:/example.org").toURL());
        list.add("ok");

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("keep", "\u017elu\u0165ou\u010dk\u00fd k\u016f\u0148");
        input.put("missing", null);
        input.put("secret", "hidden");
        input.put("site", URI.create("file:/example.com").toURL());
        input.put("id", uuid);
        input.put("date", new Date(0L));
        input.put("nested", nested);
        input.put("list", list);

        String json = generator.toJson(input);
        Map<String, Object> parsed = asMap(new JsonSlurper().parseText(json));

        assertThat(generator.isExcludingFieldsNamed("secret")).isTrue();
        assertThat(generator.isExcludingValues(null)).isTrue();
        assertThat(generator.isExcludingValues(URI.create("file:/example.net").toURL())).isTrue();
        assertThat(json).contains("\u017elu\u0165ou\u010dk\u00fd k\u016f\u0148");
        assertThat(parsed.get("keep")).isEqualTo("\u017elu\u0165ou\u010dk\u00fd k\u016f\u0148");
        assertThat(parsed).doesNotContainKeys("missing", "secret", "site");
        assertThat(parsed.get("id")).isEqualTo("id:123e4567");
        assertThat(parsed.get("date")).isEqualTo("1970-01-01");
        assertThat(asMap(parsed.get("nested"))).doesNotContainKey("skip");
        assertThat(asMap(parsed.get("nested")).get("ok")).isEqualTo(1);
        assertThat(asList(parsed.get("list"))).containsExactly("ok");
    }

    @Test
    void buildsJsonDocumentsWithJsonBuilder() throws Exception {
        List<Map<String, Object>> books = List.of(
                Map.of("identifier", 1, "available", true),
                Map.of("identifier", 2, "available", false));

        Map<String, Object> catalog = new LinkedHashMap<>();
        catalog.put("title", "Groovy JSON");
        catalog.put("active", true);
        catalog.put("books", books);
        catalog.put("tags", List.of("parser", "writer"));
        catalog.put("metadata", Map.of("createdBy", "JsonBuilder", "version", 4));

        JsonBuilder builder = new JsonBuilder();
        Object content = builder.call(Map.of("catalog", catalog));

        String json = builder.toString();
        String prettyJson = builder.toPrettyString();
        StringWriter written = new StringWriter();
        builder.writeTo(written);
        Map<String, Object> parsed = asMap(new JsonSlurper().parseText(json));
        Map<String, Object> contentMap = asMap(content);
        Map<String, Object> contentCatalog = asMap(contentMap.get("catalog"));
        Map<String, Object> parsedCatalog = asMap(parsed.get("catalog"));

        assertThat(contentCatalog.get("title")).isEqualTo("Groovy JSON");
        assertThat(written.toString()).isEqualTo(json);
        assertThat(prettyJson).contains("\n    \"catalog\"");
        assertThat(parsedCatalog.get("title")).isEqualTo("Groovy JSON");
        assertThat(parsedCatalog.get("active")).isEqualTo(true);
        assertThat(asList(parsedCatalog.get("books")))
                .extracting(entry -> asMap(entry).get("identifier"))
                .containsExactly(1, 2);
        assertThat(asList(parsedCatalog.get("books")))
                .extracting(entry -> asMap(entry).get("available"))
                .containsExactly(true, false);
        assertThat(asList(parsedCatalog.get("tags"))).containsExactly("parser", "writer");
        assertThat(asMap(parsedCatalog.get("metadata")).get("createdBy")).isEqualTo("JsonBuilder");
        assertThat(asMap(parsedCatalog.get("metadata")).get("version")).isEqualTo(4);
    }

    @Test
    void streamsJsonDocumentsWithStreamingJsonBuilder() throws Exception {
        StringWriter writer = new StringWriter();
        StreamingJsonBuilder builder = new StreamingJsonBuilder(writer);

        List<Map<String, String>> peopleRows = List.of(
                Map.of("name", "Ada", "language", "Groovy"),
                Map.of("name", "Grace", "language", "COBOL"));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("message", "streamed");
        payload.put("answer", 42);
        payload.put("raw", JsonOutput.unescaped("{\"trusted\":true}"));
        payload.put("people", peopleRows);
        payload.put("values", List.of(1, 2, 3));

        builder.call(payload);

        Map<String, Object> parsed = asMap(new JsonSlurper().parseText(writer.toString()));

        assertThat(parsed.get("message")).isEqualTo("streamed");
        assertThat(((Number) parsed.get("answer")).intValue()).isEqualTo(42);
        assertThat(asMap(parsed.get("raw")).get("trusted")).isEqualTo(true);
        assertThat(asList(parsed.get("people")))
                .extracting(entry -> asMap(entry).get("name"))
                .containsExactly("Ada", "Grace");
        assertThat(asList(parsed.get("people")))
                .extracting(entry -> asMap(entry).get("language"))
                .containsExactly("Groovy", "COBOL");
        assertThat(asList(parsed.get("values"))).extracting(Object::toString).containsExactly("1", "2", "3");
    }

    @Test
    void prettyPrintsLexesAndUnescapesJson() {
        String compactJson = "{\"message\":\"\u041f\u0440\u0438\u0432\u0435\u0442\",\"items\":[1,{\"x\":true}]}";

        String prettyJson = JsonOutput.prettyPrint(compactJson, true);
        JsonLexer lexer = new JsonLexer(new StringReader(compactJson));
        List<JsonToken> tokens = new ArrayList<>();
        while (lexer.hasNext()) {
            tokens.add(lexer.next());
        }

        assertThat(prettyJson).contains("\"message\": \"\u041f\u0440\u0438\u0432\u0435\u0442\"");
        assertThat(prettyJson).contains("\n    \"items\": [");
        assertThat(tokens).extracting(JsonToken::getType).contains(
                JsonTokenType.OPEN_CURLY,
                JsonTokenType.STRING,
                JsonTokenType.COLON,
                JsonTokenType.OPEN_BRACKET,
                JsonTokenType.TRUE,
                JsonTokenType.CLOSE_CURLY);
        assertThat(tokens.stream()
                .filter(token -> token.getType() == JsonTokenType.STRING)
                .findFirst()
                .orElseThrow()
                .getValue()).isEqualTo("message");
        assertThat(JsonLexer.unescape("line\\nvalue\\u0021")).isEqualTo("line\nvalue!");
    }

    @Test
    void indexOverlaySlurperConvertsIsoDateStringsWhenDateCheckingIsEnabled() {
        String json = """
                {
                    "utc":"1994-11-05T08:15:30Z",
                    "offset":"1994-11-05T08:15:30-05:00",
                    "millis":"2013-12-14T01:55:33.412Z",
                    "plain":"1994-11-05"
                }
                """;

        Map<String, Object> parsedWithDates = asMap(new JsonSlurper()
                .setType(JsonParserType.INDEX_OVERLAY)
                .setCheckDates(true)
                .parseText(json));
        Map<String, Object> parsedWithoutDates = asMap(new JsonSlurper()
                .setType(JsonParserType.INDEX_OVERLAY)
                .setCheckDates(false)
                .parseText(json));

        assertThat(parsedWithDates.get("utc")).isInstanceOf(Date.class);
        assertThat(((Date) parsedWithDates.get("utc")).getTime())
                .isEqualTo(Instant.parse("1994-11-05T08:15:30Z").toEpochMilli());
        assertThat(parsedWithDates.get("offset")).isInstanceOf(Date.class);
        assertThat(((Date) parsedWithDates.get("offset")).getTime())
                .isEqualTo(OffsetDateTime.parse("1994-11-05T08:15:30-05:00").toInstant().toEpochMilli());
        assertThat(parsedWithDates.get("millis")).isInstanceOf(Date.class);
        assertThat(((Date) parsedWithDates.get("millis")).getTime())
                .isEqualTo(Instant.parse("2013-12-14T01:55:33.412Z").toEpochMilli());
        assertThat(parsedWithDates.get("plain")).isEqualTo("1994-11-05");
        assertThat(parsedWithoutDates.get("utc")).isEqualTo("1994-11-05T08:15:30Z");
        assertThat(parsedWithoutDates.get("offset")).isEqualTo("1994-11-05T08:15:30-05:00");
        assertThat(parsedWithoutDates.get("millis")).isEqualTo("2013-12-14T01:55:33.412Z");
    }

    @Test
    void classicSlurperParsesObjectsArraysAndNulls() {
        String json = "{\"value\":1,\"items\":[true,false,null],\"nested\":{\"name\":\"classic\"}}";
        Map<String, Object> parsed = asMap(new JsonSlurperClassic().parseText(json));

        assertThat(((Number) parsed.get("value")).intValue()).isEqualTo(1);
        assertThat(asList(parsed.get("items"))).containsExactly(true, false, null);
        assertThat(asMap(parsed.get("nested")).get("name")).isEqualTo("classic");

        assertThatThrownBy(() -> new JsonSlurper().parseText(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Text must not be null or empty");
    }

    private static void assertParsedDocument(Object parsed) {
        Map<String, Object> parsedMap = asMap(parsed);
        assertThat(parsedMap.get("greeting")).isEqualTo("h\u00e9llo");
        assertThat(asList(parsedMap.get("values"))).extracting(Object::toString).containsExactly("1", "2", "3");
        assertThat(asMap(parsedMap.get("nested")).get("ok")).isEqualTo(true);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asList(Object value) {
        return (List<Object>) value;
    }
}
