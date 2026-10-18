/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_module.jackson_module_kotlin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.kotlin.KotlinModule;
import java.util.ArrayList;
import java.util.List;
import kotlin.sequences.Sequence;
import kotlin.sequences.SequencesKt;
import org.junit.jupiter.api.Test;

/// Kotlin lazy sequences (`kotlin.sequences.Sequence`) written and read by the Kotlin module.
///
/// A Java audit service shares its export format with Kotlin consumers, which model a batch
/// of audit lines as a lazy `Sequence`. The service builds the batch as a filtered and mapped
/// sequence, writes it with an `ObjectMapper` that registers `KotlinModule`, and a reader
/// binds the stored JSON back into sequences it walks to raise alerts. The module reads a
/// sequence's elements as untyped JSON values, so the batch carries strings and numbers.
class KotlinSequenceBindingTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new KotlinModule.Builder().build());

    /// The exported batch: a source name, the `actor:action:severity` lines it covers, and
    /// their severities.
    public static final class AuditBatch {
        public String source;
        public Sequence<String> lines;
        public Sequence<Integer> severities;

        public AuditBatch() {
        }

        AuditBatch(String source, Sequence<String> lines) {
            this.source = source;
            this.lines = lines;
            this.severities = SequencesKt.map(lines, KotlinSequenceBindingTest::severity);
        }
    }

    @Test
    void exportsFilteredSequenceAndReadsItBack() throws Exception {
        Sequence<String> rawLog = SequencesKt.sequenceOf(
                " alice:login:1", "bob:delete-user:4 ", "carol:read:1", " dave:grant-admin:5");
        Sequence<String> important = SequencesKt.filter(
                SequencesKt.map(rawLog, String::strip), line -> severity(line) >= 3);

        String json = MAPPER.writeValueAsString(new AuditBatch("billing", important));

        JsonNode tree = MAPPER.readTree(json);
        assertEquals("billing", tree.get("source").asText());
        JsonNode lines = tree.get("lines");
        assertTrue(lines.isArray());
        assertEquals(2, lines.size());
        assertEquals("bob:delete-user:4", lines.get(0).asText());
        assertEquals("dave:grant-admin:5", lines.get(1).asText());
        assertEquals(5, tree.get("severities").get(1).asInt());

        AuditBatch restored = MAPPER.readValue(json, AuditBatch.class);

        assertEquals("billing", restored.source);
        List<String> alerts = new ArrayList<>();
        for (String line : SequencesKt.asIterable(restored.lines)) {
            String[] parts = line.split(":");
            alerts.add(parts[0] + " -> " + parts[1]);
        }
        assertEquals(List.of("bob -> delete-user", "dave -> grant-admin"), alerts);
        assertEquals(List.of(4, 5), SequencesKt.toList(restored.severities));
    }

    @Test
    void readsHandWrittenBatchIntoLazySequences() throws Exception {
        String json = """
                {
                  "source": "hr",
                  "lines": ["erin:export:2", "frank:wipe:5", "gina:export:3"],
                  "severities": [2, 5, 3]
                }
                """;

        AuditBatch batch = MAPPER.readValue(json, AuditBatch.class);

        assertEquals("hr", batch.source);
        assertEquals(3, SequencesKt.count(batch.lines));
        List<String> exporters = SequencesKt.toList(SequencesKt.map(
                SequencesKt.filter(batch.lines, line -> line.contains(":export:")),
                line -> line.substring(0, line.indexOf(':'))));
        assertEquals(List.of("erin", "gina"), exporters);
        assertEquals(10, SequencesKt.sumOfInt(batch.severities));
        assertEquals(5, SequencesKt.maxOrNull(batch.severities));

        String rewritten = MAPPER.writeValueAsString(batch);
        assertEquals(json.replaceAll("\\s", ""), rewritten);
    }

    private static int severity(String line) {
        return Integer.parseInt(line.substring(line.lastIndexOf(':') + 1));
    }
}
