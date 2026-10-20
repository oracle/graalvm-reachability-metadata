/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_code_gson.gson;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.FormattingStyle;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Output formatting of serialized JSON with `FormattingStyle`, `setPrettyPrinting`, and
 * `JsonWriter` indentation.
 *
 * A deployment tool writes service manifests in three layouts: a compact single line for the
 * wire, a human-readable file that operators review and edit, and a one-line log rendering with
 * spaces after separators. Repository conventions differ, so the tool also honors a team's tab
 * indentation and Windows line endings, rejects layout settings that would produce invalid JSON
 * whitespace, and reads every layout back to the same manifest.
 */
public class DeploymentManifestFormattingTest {

    private static final Manifest MANIFEST = new Manifest(
            "billing-api",
            3,
            List.of(new Port("http", 8080), new Port("metrics", 9090)),
            orderedLabels(),
            List.of());

    @TempDir
    Path workDir;

    @Test
    void defaultGsonWritesTheCompactWireForm() {
        String wire = new Gson().toJson(MANIFEST);

        assertEquals("""
                {"service":"billing-api","replicas":3,"ports":[{"name":"http","port":8080},\
                {"name":"metrics","port":9090}],"labels":{"team":"payments","tier":"backend"},\
                "volumes":[]}""", wire);
        assertEquals(wire, new GsonBuilder().setFormattingStyle(FormattingStyle.COMPACT).create().toJson(MANIFEST));
    }

    @Test
    void prettyPrintingProducesTheReviewedFileLayout() throws IOException {
        Gson fileGson = new GsonBuilder().setPrettyPrinting().create();
        Path file = workDir.resolve("billing-api.json");

        try (Writer out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            fileGson.toJson(MANIFEST, out);
        }

        String written = Files.readString(file, StandardCharsets.UTF_8);
        assertEquals("""
                {
                  "service": "billing-api",
                  "replicas": 3,
                  "ports": [
                    {
                      "name": "http",
                      "port": 8080
                    },
                    {
                      "name": "metrics",
                      "port": 9090
                    }
                  ],
                  "labels": {
                    "team": "payments",
                    "tier": "backend"
                  },
                  "volumes": []
                }""", written);

        String explicitPretty = new GsonBuilder().setFormattingStyle(FormattingStyle.PRETTY).create().toJson(MANIFEST);
        assertEquals(written, explicitPretty);

        try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            assertEquals(MANIFEST, fileGson.fromJson(in, Manifest.class));
        }
    }

    @Test
    void teamConventionsCustomizeIndentAndLineEndings() {
        FormattingStyle windowsTabs = FormattingStyle.PRETTY.withIndent("\t").withNewline("\r\n");
        Gson teamGson = new GsonBuilder().setFormattingStyle(windowsTabs).create();

        String manifest = teamGson.toJson(new Port("http", 8080));

        assertEquals("{\r\n\t\"name\": \"http\",\r\n\t\"port\": 8080\r\n}", manifest);
        assertEquals("\t", windowsTabs.getIndent());
        assertEquals("\r\n", windowsTabs.getNewline());
        assertTrue(windowsTabs.usesSpaceAfterSeparators());

        FormattingStyle tight = FormattingStyle.PRETTY.withSpaceAfterSeparators(false);
        assertEquals("""
                {
                  "name":"http",
                  "port":8080
                }""", new GsonBuilder().setFormattingStyle(tight).create().toJson(new Port("http", 8080)));

        assertEquals("  ", FormattingStyle.PRETTY.getIndent(), "derived styles leave the base constant untouched");
        assertTrue(FormattingStyle.PRETTY.usesSpaceAfterSeparators());
    }

    @Test
    void logLineSeparatesItemsWithSpacesOnASingleLine() {
        FormattingStyle logLine = FormattingStyle.COMPACT.withSpaceAfterSeparators(true);
        Gson logGson = new GsonBuilder().setFormattingStyle(logLine).create();

        StringBuilder log = new StringBuilder("deploy ");
        logGson.toJson(new Manifest("cache", 1, List.of(new Port("redis", 6379)), Map.of("tier", "data"),
                List.of("/var/lib/cache")), log);

        assertEquals("""
                deploy {"service": "cache", "replicas": 1, "ports": [{"name": "redis", "port": 6379}], \
                "labels": {"tier": "data"}, "volumes": ["/var/lib/cache"]}""", log.toString());
        assertFalse(log.toString().contains("\n"));
        assertEquals("", logLine.getIndent());
        assertEquals("", logLine.getNewline());
    }

    @Test
    void derivedInstancesSwitchLayoutWithoutLosingOtherSettings() {
        Gson wireGson = new GsonBuilder().serializeNulls().create();
        Gson reviewGson = wireGson.newBuilder().setPrettyPrinting().create();
        Port unnamed = new Port(null, 7000);

        assertEquals("{\"name\":null,\"port\":7000}", wireGson.toJson(unnamed));
        assertEquals("""
                {
                  "name": null,
                  "port": 7000
                }""", reviewGson.toJson(unnamed));

        JsonElement tree = reviewGson.toJsonTree(unnamed);
        assertEquals("{\"name\":null,\"port\":7000}", tree.toString(), "tree rendering ignores the Gson layout");
        assertEquals(reviewGson.toJson(unnamed), reviewGson.toJson(tree));
    }

    @Test
    void writerCreatedByGsonInheritsTheLayoutAndAcceptsIndentShortcut() throws IOException {
        FormattingStyle windowsTabs = FormattingStyle.PRETTY.withIndent("\t").withNewline("\r\n");
        Gson teamGson = new GsonBuilder().setFormattingStyle(windowsTabs).create();

        StringWriter buffer = new StringWriter();
        try (JsonWriter writer = teamGson.newJsonWriter(buffer)) {
            assertSame(windowsTabs, writer.getFormattingStyle());
            writer.beginObject().name("revision").value(42L).endObject();
        }
        assertEquals("{\r\n\t\"revision\": 42\r\n}", buffer.toString());

        StringWriter shortcut = new StringWriter();
        try (JsonWriter writer = new JsonWriter(shortcut)) {
            writer.setIndent("    ");
            assertEquals("    ", writer.getFormattingStyle().getIndent());
            assertEquals("\n", writer.getFormattingStyle().getNewline());
            writer.beginArray().value("blue").value("green").endArray();
        }
        assertEquals("[\n    \"blue\",\n    \"green\"\n]", shortcut.toString());

        StringWriter cleared = new StringWriter();
        try (JsonWriter writer = new JsonWriter(cleared)) {
            writer.setIndent("    ");
            writer.setIndent("");
            writer.beginArray().value(1).value(2).endArray();
        }
        assertEquals("[1,2]", cleared.toString());
    }

    @Test
    void rejectsLayoutsThatWouldWriteInvalidWhitespace() {
        IllegalArgumentException badIndent = assertThrows(IllegalArgumentException.class,
                () -> FormattingStyle.PRETTY.withIndent("--"));
        assertTrue(badIndent.getMessage().contains("indent"), badIndent.getMessage());

        IllegalArgumentException badNewline = assertThrows(IllegalArgumentException.class,
                () -> FormattingStyle.PRETTY.withNewline("<br>"));
        assertTrue(badNewline.getMessage().contains("newline"), badNewline.getMessage());
    }

    @Test
    void everyLayoutReadsBackToTheSameManifest() {
        Gson reader = new Gson();
        List<FormattingStyle> layouts = List.of(
                FormattingStyle.COMPACT,
                FormattingStyle.PRETTY,
                FormattingStyle.COMPACT.withSpaceAfterSeparators(true),
                FormattingStyle.PRETTY.withIndent("\t").withNewline("\r\n"),
                FormattingStyle.PRETTY.withIndent(" \t").withNewline("\r").withSpaceAfterSeparators(false));

        for (FormattingStyle layout : layouts) {
            String json = new GsonBuilder().setFormattingStyle(layout).create().toJson(MANIFEST);
            assertEquals(MANIFEST, reader.fromJson(json, Manifest.class), json);
        }
    }

    private static Map<String, String> orderedLabels() {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("team", "payments");
        labels.put("tier", "backend");
        return labels;
    }

    /// One exposed container port of a service.
    record Port(String name, int port) {
    }

    /// The deployment description the tool writes and reads back.
    record Manifest(String service, int replicas, List<Port> ports, Map<String, String> labels,
            List<String> volumes) {
    }
}
