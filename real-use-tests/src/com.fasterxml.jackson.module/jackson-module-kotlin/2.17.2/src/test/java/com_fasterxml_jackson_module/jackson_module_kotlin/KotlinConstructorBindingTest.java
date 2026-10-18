/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_module.jackson_module_kotlin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.kotlin.KotlinModule;
import com.fasterxml.jackson.module.kotlin.MissingKotlinParameterException;
import java.util.ArrayList;
import java.util.List;
import kotlin.KotlinVersion;
import kotlin.collections.IndexedValue;
import kotlin.ranges.IntRange;
import kotlin.text.MatchGroup;
import kotlin.text.MatchResult;
import kotlin.text.Regex;
import org.junit.jupiter.api.Test;

/// Kotlin primary-constructor binding and missing-parameter validation by the Kotlin module.
///
/// A Java search service exchanges match reports with Kotlin clients, which model each hit
/// with Kotlin's own constructor-only classes: `IndexedValue` for the hit position,
/// `MatchGroup` for the matched text and its range, and `KotlinVersion` for the client
/// version. None of them has a default constructor or setters, so the Kotlin module must
/// bind JSON through their Kotlin primary constructors and reject a report that omits a
/// non-null constructor parameter.
class KotlinConstructorBindingTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new KotlinModule.Builder().build());

    private static final TypeReference<List<IndexedValue<MatchGroup>>> HITS =
            new TypeReference<>() {
            };

    /// The report a search node sends back for one query.
    public static final class MatchReport {
        public String query;
        public KotlinVersion clientVersion;
        public List<IndexedValue<MatchGroup>> hits;

        public MatchReport() {
        }

        MatchReport(String query, KotlinVersion clientVersion, List<IndexedValue<MatchGroup>> hits) {
            this.query = query;
            this.clientVersion = clientVersion;
            this.hits = hits;
        }
    }

    @Test
    void sendsAndReceivesMatchReport() throws Exception {
        List<String> documents = List.of("no tickets here", "see JIRA-12 and JIRA-7", "OPS-300 done");
        Regex ticket = new Regex("[A-Z]+-[0-9]+");
        List<IndexedValue<MatchGroup>> hits = new ArrayList<>();
        for (int i = 0; i < documents.size(); i++) {
            MatchResult match = ticket.find(documents.get(i), 0);
            if (match != null) {
                hits.add(new IndexedValue<>(i, match.getGroups().get(0)));
            }
        }
        MatchReport sent = new MatchReport("tickets", new KotlinVersion(1, 9, 24), hits);

        String json = MAPPER.writeValueAsString(sent);
        MatchReport received = MAPPER.readValue(json, MatchReport.class);

        assertEquals("tickets", received.query);
        assertEquals(new KotlinVersion(1, 9, 24), received.clientVersion);
        assertTrue(received.clientVersion.isAtLeast(1, 9));
        assertFalse(received.clientVersion.isAtLeast(2, 0));
        assertEquals(2, received.hits.size());
        IndexedValue<MatchGroup> first = received.hits.get(0);
        assertEquals(1, first.getIndex());
        assertEquals("JIRA-12", first.getValue().getValue());
        assertEquals(new IntRange(4, 10), first.getValue().getRange());
        assertEquals(hits, received.hits);

        String document = documents.get(received.hits.get(1).getIndex());
        IntRange range = received.hits.get(1).getValue().getRange();
        assertEquals("OPS-300", document.substring(range.getFirst(), range.getLast() + 1));
    }

    @Test
    void readsHandWrittenHitsThroughPrimaryConstructors() throws Exception {
        String json = """
                [
                  {"index": 4, "value": {"value": "abc", "range": {"start": 2, "end": 4}}},
                  {"value": {"range": {"start": 0, "end": 1}, "value": "xy"}, "index": 9}
                ]
                """;

        List<IndexedValue<MatchGroup>> hits = MAPPER.readValue(json, HITS);

        assertEquals(List.of(
                new IndexedValue<>(4, new MatchGroup("abc", new IntRange(2, 4))),
                new IndexedValue<>(9, new MatchGroup("xy", new IntRange(0, 1)))), hits);
    }

    @Test
    void rejectsHitMissingNonNullParameter() {
        String json = """
                [{"index": 3, "value": {"range": {"start": 0, "end": 2}}}]
                """;

        MissingKotlinParameterException error = assertThrows(MissingKotlinParameterException.class,
                () -> MAPPER.readValue(json, HITS));

        assertEquals("value", error.getParameter().getName());
        assertTrue(error.getMessage().contains("MatchGroup"), error.getMessage());
    }

    @Test
    void rejectsExplicitNullForNonNullParameter() {
        String json = """
                {"query": "q", "clientVersion": {"major": 2, "minor": 0, "patch": 0},
                 "hits": [{"index": 0, "value": {"value": null, "range": {"start": 0, "end": 0}}}]}
                """;

        MissingKotlinParameterException error = assertThrows(MissingKotlinParameterException.class,
                () -> MAPPER.readValue(json, MatchReport.class));

        assertEquals("value", error.getParameter().getName());
    }
}
