/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_code_gson.gson;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonStreamParser;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Token-level streaming of JSON with `JsonReader`, `JsonWriter`, and `JsonStreamParser`.
 *
 * An audit service exports a large event log as one JSON array, written event by event with a
 * `JsonWriter`, and later scans it with a `JsonReader` without ever materializing the whole
 * document: it peeks token types, skips subtrees it does not need, aggregates per-user
 * statistics, and reports malformed input with the reader's JSON path. It also reads a stream of
 * concatenated documents from a lenient collector feed.
 */
public class AuditLogStreamingTest {

    private static final int EVENT_COUNT = 5_000;

    @TempDir
    Path workDir;

    @Test
    void writesAndScansALargeEventLogFile() throws IOException {
        Path file = workDir.resolve("audit.json");

        try (Writer out = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
                JsonWriter writer = new JsonWriter(out)) {
            AuditExporter.export(writer, EVENT_COUNT);
        }

        AuditSummary summary;
        try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8);
                JsonReader reader = new JsonReader(in)) {
            summary = AuditScanner.scan(reader);
        }

        assertEquals(EVENT_COUNT, summary.totalEvents());
        assertEquals(EVENT_COUNT / 7 + 1, summary.failedEvents());
        assertEquals(Map.of("alice", 1667L, "bob", 1667L, "carol", 1666L), summary.eventsPerUser());
        long expectedBytes = 0;
        for (int i = 0; i < EVENT_COUNT; i++) {
            expectedBytes += 100L * (i % 13);
        }
        assertEquals(expectedBytes, summary.bytesTransferred());
        assertEquals(EVENT_COUNT / 50, summary.eventsWithoutDetails());
    }

    @Test
    void writerProducesIndentedOutputAndOmitsNullsWhenConfigured() throws IOException {
        StringWriter out = new StringWriter();
        try (JsonWriter writer = new JsonWriter(out)) {
            writer.setIndent("  ");
            writer.setSerializeNulls(false);
            writer.beginObject();
            writer.name("service").value("billing");
            writer.name("owner").nullValue();
            writer.name("healthy").value(true);
            writer.name("latencyMs").value(12.5);
            writer.name("tags").beginArray().value("eu").value("prod").endArray();
            writer.name("raw").jsonValue("{\"k\":1}");
            writer.endObject();
        }

        String expected = """
                {
                  "service": "billing",
                  "healthy": true,
                  "latencyMs": 12.5,
                  "tags": [
                    "eu",
                    "prod"
                  ],
                  "raw": {"k":1}
                }""";
        assertEquals(expected, out.toString());
    }

    @Test
    void writerEscapesHtmlCharactersOnlyWhenHtmlSafe() throws IOException {
        StringWriter plain = new StringWriter();
        try (JsonWriter writer = new JsonWriter(plain)) {
            writer.beginArray().value("<b>&'</b>").endArray();
        }
        StringWriter htmlSafe = new StringWriter();
        try (JsonWriter writer = new JsonWriter(htmlSafe)) {
            writer.setHtmlSafe(true);
            writer.beginArray().value("<b>&'</b>").endArray();
        }

        assertEquals("[\"<b>&'</b>\"]", plain.toString());
        assertEquals("[\"\\u003cb\\u003e\\u0026\\u0027\\u003c/b\\u003e\"]", htmlSafe.toString());
    }

    @Test
    void malformedEventIsReportedWithItsJsonPath() {
        String json = """
                [
                  {"id": 1, "user": "alice", "action": "login", "bytes": 10},
                  {"id": 2, "user": "bob", "action": "upload", "bytes": "lots"}
                ]""";

        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> {
            try (JsonReader reader = new JsonReader(new StringReader(json))) {
                AuditScanner.scan(reader);
            }
        });

        assertTrue(failure.getMessage().contains("$[1].bytes"), failure.getMessage());
    }

    @Test
    void strictReaderRejectsCommentsThatALenientReaderAccepts() throws IOException {
        String feed = """
                // collector heartbeat
                {'host': 'edge-1', load: 0.75, unquoted: yes}
                """;

        try (JsonReader strict = new JsonReader(new StringReader(feed))) {
            strict.setStrictness(Strictness.STRICT);
            assertThrows(MalformedJsonException.class, strict::beginObject);
        }

        try (JsonReader lenient = new JsonReader(new StringReader(feed))) {
            lenient.setStrictness(Strictness.LENIENT);
            lenient.beginObject();
            assertEquals("host", lenient.nextName());
            assertEquals("edge-1", lenient.nextString());
            assertEquals("load", lenient.nextName());
            assertEquals(0.75, lenient.nextDouble());
            assertEquals("unquoted", lenient.nextName());
            assertEquals(JsonToken.STRING, lenient.peek());
            assertEquals("yes", lenient.nextString());
            lenient.endObject();
            assertEquals(JsonToken.END_DOCUMENT, lenient.peek());
        }
    }

    @Test
    void streamParserReadsConcatenatedCollectorDocuments() {
        String feed = """
                {"host": "edge-1", "load": 0.5}
                {"host": "edge-2", "load": 0.9}
                [1, 2, 3]
                "end-of-batch"
                """;

        JsonStreamParser parser = new JsonStreamParser(feed);
        List<String> hosts = new ArrayList<>();
        int arrays = 0;
        String trailer = null;
        while (parser.hasNext()) {
            JsonElement element = parser.next();
            if (element.isJsonObject()) {
                hosts.add(element.getAsJsonObject().get("host").getAsString());
            } else if (element.isJsonArray()) {
                arrays++;
                assertEquals(3, element.getAsJsonArray().size());
            } else {
                trailer = element.getAsString();
            }
        }

        assertEquals(List.of("edge-1", "edge-2"), hosts);
        assertEquals(1, arrays);
        assertEquals("end-of-batch", trailer);
        assertFalse(parser.hasNext());
    }

    /** Writes the audit log one event at a time so the full log never sits in memory. */
    static final class AuditExporter {

        private static final String[] USERS = {"alice", "bob", "carol"};

        private AuditExporter() {
        }

        static void export(JsonWriter writer, int count) throws IOException {
            writer.beginArray();
            for (int i = 0; i < count; i++) {
                writer.beginObject();
                writer.name("id").value(i);
                writer.name("user").value(USERS[i % USERS.length]);
                writer.name("action").value(i % 2 == 0 ? "download" : "upload");
                writer.name("bytes").value(100L * (i % 13));
                writer.name("ok").value(i % 7 != 0);
                if (i % 50 == 0) {
                    writer.name("details").nullValue();
                } else {
                    writer.name("details").beginObject();
                    writer.name("client").value("cli/" + (i % 4));
                    writer.name("headers").beginArray().value("x-trace").value("x-span").endArray();
                    writer.endObject();
                }
                writer.endObject();
            }
            writer.endArray();
        }
    }

    /** Aggregates statistics from the log by walking its tokens. */
    static final class AuditScanner {

        private AuditScanner() {
        }

        static AuditSummary scan(JsonReader reader) throws IOException {
            Map<String, Long> perUser = new TreeMap<>();
            long total = 0;
            long failed = 0;
            long bytes = 0;
            long withoutDetails = 0;

            reader.beginArray();
            while (reader.hasNext()) {
                reader.beginObject();
                String user = null;
                boolean ok = true;
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    switch (name) {
                        case "user" -> user = reader.nextString();
                        case "ok" -> ok = reader.nextBoolean();
                        case "bytes" -> bytes += readBytes(reader);
                        case "details" -> {
                            if (reader.peek() == JsonToken.NULL) {
                                reader.nextNull();
                                withoutDetails++;
                            } else {
                                reader.skipValue();
                            }
                        }
                        default -> reader.skipValue();
                    }
                }
                reader.endObject();
                total++;
                if (!ok) {
                    failed++;
                }
                perUser.merge(user, 1L, Long::sum);
            }
            reader.endArray();
            return new AuditSummary(total, failed, bytes, withoutDetails, perUser);
        }

        private static long readBytes(JsonReader reader) throws IOException {
            if (reader.peek() != JsonToken.NUMBER) {
                throw new IllegalStateException("Expected a byte count at " + reader.getPath());
            }
            return reader.nextLong();
        }
    }

    record AuditSummary(
            long totalEvents,
            long failedEvents,
            long bytesTransferred,
            long eventsWithoutDetails,
            Map<String, Long> eventsPerUser) {
    }
}
