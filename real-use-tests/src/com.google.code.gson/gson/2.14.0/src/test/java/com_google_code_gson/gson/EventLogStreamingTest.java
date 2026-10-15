/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_code_gson.gson;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.FormattingStyle;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import com.google.gson.stream.MalformedJsonException;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Streaming JSON processing with the token-level JsonReader and JsonWriter API.
 *
 * A log ingestion tool writes a large audit log event by event, then reads it back token by
 * token to aggregate per-service statistics without binding whole documents to objects.
 */
public class EventLogStreamingTest {

    @Test
    void writesEventLogAndAggregatesItByStreaming(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("events.json");
        try (Writer out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            EventLogWriter writer = new EventLogWriter(out);
            writer.begin("eu-central");
            for (int i = 0; i < 300; i++) {
                String service = i % 3 == 0 ? "billing" : "search";
                writer.event(i, service, i % 10 == 0 ? "ERROR" : "INFO", 12.5 + i % 4,
                        i % 7 == 0 ? null : "user-" + (i % 5), List.of("t" + i % 2, "core"));
            }
            writer.end();
        }

        LogSummary summary;
        try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            summary = EventLogAggregator.aggregate(new JsonReader(in));
        }

        assertEquals("eu-central", summary.region);
        assertEquals(300, summary.total);
        assertEquals(100, summary.byService.get("billing").count);
        assertEquals(200, summary.byService.get("search").count);
        assertEquals(30, summary.errors);
        assertEquals(43, summary.anonymous);
        assertEquals(12.5 + 1.5, summary.byService.get("billing").averageLatency(), 0.01);
        assertEquals(Set.of("core", "t0", "t1"), summary.distinctTags);
    }

    @Test
    void skipsUnknownFieldsAndNestedStructures() throws IOException {
        String json = """
                {
                  "region": "us-east",
                  "schema": {"version": 3, "fields": ["id", "service"], "extra": {"deep": [1, [2, 3]]}},
                  "events": [
                    {"id": 1, "service": "auth", "level": "WARN", "latencyMs": 4,
                     "context": {"ip": "10.0.0.1", "headers": {"x": ["a", "b"]}}, "user": "ana"},
                    {"id": 2, "flag": true, "service": "auth", "level": "INFO", "latencyMs": 6.0,
                     "user": null, "tags": []}
                  ],
                  "trailer": null
                }
                """;

        LogSummary summary = EventLogAggregator.aggregate(new JsonReader(new StringReader(json)));

        assertEquals("us-east", summary.region);
        assertEquals(2, summary.total);
        assertEquals(1, summary.anonymous);
        assertEquals(5.0, summary.byService.get("auth").averageLatency(), 0.0001);
        assertTrue(summary.distinctTags.isEmpty());
    }

    @Test
    void reportsThePathOfMalformedInput() {
        String json = """
                {"region": "ap", "events": [
                  {"id": 1, "service": "cart", "level": "INFO", "latencyMs": 1},
                  {"id": 2, "service": "cart", "level": "INFO", "latencyMs": }
                ]}
                """;

        MalformedJsonException failure = assertThrows(MalformedJsonException.class,
                () -> EventLogAggregator.aggregate(new JsonReader(new StringReader(json))));

        assertTrue(failure.getMessage().contains("$.events[1].latencyMs"), failure.getMessage());
    }

    @Test
    void readsHandEditedLogsInLenientMode() throws IOException {
        String json = """
                // exported by an operator, then edited by hand
                {region: 'staging', events: [
                  {id: 1, service: ops, level: 'INFO', latencyMs: 2},
                  {id: 2; service = ops; level = ERROR; latencyMs = "8"}
                ]}
                """;

        JsonReader strict = new JsonReader(new StringReader(json));
        strict.setStrictness(Strictness.STRICT);
        assertThrows(MalformedJsonException.class, () -> EventLogAggregator.aggregate(strict));

        JsonReader lenient = new JsonReader(new StringReader(json));
        lenient.setStrictness(Strictness.LENIENT);
        LogSummary summary = EventLogAggregator.aggregate(lenient);

        assertEquals("staging", summary.region);
        assertEquals(2, summary.total);
        assertEquals(1, summary.errors);
        assertEquals(5.0, summary.byService.get("ops").averageLatency(), 0.0001);
    }

    @Test
    void writerControlsFormattingEscapingAndNulls() throws IOException {
        StringWriter compact = new StringWriter();
        JsonWriter writer = new JsonWriter(compact);
        writer.setHtmlSafe(true);
        writer.setSerializeNulls(false);
        writer.beginObject()
                .name("message").value("<b>disk & cpu</b> 'hot'")
                .name("dropped").nullValue()
                .name("raw").jsonValue("{\"pre\":[1,2]}")
                .name("ratio").value(0.25)
                .name("count").value(7L)
                .name("ok").value(true)
                .endObject();
        writer.close();

        assertEquals("{\"message\":\"\\u003cb\\u003edisk \\u0026 cpu\\u003c/b\\u003e \\u0027hot\\u0027\","
                + "\"raw\":{\"pre\":[1,2]},\"ratio\":0.25,\"count\":7,\"ok\":true}", compact.toString());

        StringWriter pretty = new StringWriter();
        JsonWriter prettyWriter = new JsonWriter(pretty);
        prettyWriter.setFormattingStyle(FormattingStyle.PRETTY.withIndent("    ").withNewline("\n"));
        prettyWriter.beginObject().name("level").value("INFO").name("tags").beginArray().value("a").endArray()
                .endObject();
        prettyWriter.close();

        assertEquals("""
                {
                    "level": "INFO",
                    "tags": [
                        "a"
                    ]
                }""", pretty.toString());
    }

    @Test
    void rejectsStructurallyInvalidWrites() throws IOException {
        JsonWriter writer = new JsonWriter(new StringWriter());
        writer.beginObject();

        assertThrows(IllegalStateException.class, () -> writer.value("value without a name"));
        assertThrows(IllegalStateException.class, writer::endArray);
        assertThrows(IllegalArgumentException.class, () -> writer.name("ratio").value(Double.NaN));
    }

    /** Writes the audit log one event at a time, never holding the whole document in memory. */
    static final class EventLogWriter {

        private final JsonWriter json;

        EventLogWriter(Writer out) {
            this.json = new JsonWriter(out);
            json.setIndent("  ");
        }

        void begin(String region) throws IOException {
            json.beginObject().name("region").value(region).name("events").beginArray();
        }

        void event(long id, String service, String level, double latencyMs, String user, List<String> tags)
                throws IOException {
            json.beginObject()
                    .name("id").value(id)
                    .name("service").value(service)
                    .name("level").value(level)
                    .name("latencyMs").value(latencyMs);
            json.name("user");
            if (user == null) {
                json.nullValue();
            } else {
                json.value(user);
            }
            json.name("tags").beginArray();
            for (String tag : tags) {
                json.value(tag);
            }
            json.endArray().endObject();
        }

        void end() throws IOException {
            json.endArray().endObject();
            json.flush();
        }
    }

    /** Pulls tokens from the log and folds them into a summary. */
    static final class EventLogAggregator {

        private EventLogAggregator() {
        }

        static LogSummary aggregate(JsonReader reader) throws IOException {
            LogSummary summary = new LogSummary();
            reader.beginObject();
            while (reader.hasNext()) {
                switch (reader.nextName()) {
                    case "region" -> summary.region = reader.nextString();
                    case "events" -> readEvents(reader, summary);
                    default -> reader.skipValue();
                }
            }
            reader.endObject();
            assertEquals(JsonToken.END_DOCUMENT, reader.peek());
            return summary;
        }

        private static void readEvents(JsonReader reader, LogSummary summary) throws IOException {
            reader.beginArray();
            while (reader.hasNext()) {
                String service = null;
                double latency = 0;
                boolean error = false;
                boolean anonymous = false;
                reader.beginObject();
                while (reader.hasNext()) {
                    switch (reader.nextName()) {
                        case "service" -> service = reader.nextString();
                        case "level" -> error = "ERROR".equals(reader.nextString());
                        case "latencyMs" -> latency = reader.nextDouble();
                        case "user" -> {
                            if (reader.peek() == JsonToken.NULL) {
                                reader.nextNull();
                                anonymous = true;
                            } else {
                                reader.nextString();
                            }
                        }
                        case "tags" -> {
                            reader.beginArray();
                            while (reader.peek() == JsonToken.STRING) {
                                summary.distinctTags.add(reader.nextString());
                            }
                            reader.endArray();
                        }
                        default -> reader.skipValue();
                    }
                }
                reader.endObject();
                summary.record(service, latency, error, anonymous);
            }
            reader.endArray();
        }
    }

    /** Per-service statistics accumulated while streaming. */
    static final class ServiceStats {

        private int count;
        private double latencyTotal;

        double averageLatency() {
            return latencyTotal / count;
        }
    }

    /** What the aggregation produced. */
    static final class LogSummary {

        private final Map<String, ServiceStats> byService = new TreeMap<>();
        private final Set<String> distinctTags = new TreeSet<>();
        private String region;
        private int total;
        private int errors;
        private int anonymous;

        void record(String service, double latency, boolean error, boolean anon) {
            ServiceStats stats = byService.computeIfAbsent(service, key -> new ServiceStats());
            stats.count++;
            stats.latencyTotal += latency;
            total++;
            errors += error ? 1 : 0;
            anonymous += anon ? 1 : 0;
        }
    }
}
