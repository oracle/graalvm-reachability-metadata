/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_module.jackson_module_kotlin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.kotlin.KotlinFeature;
import com.fasterxml.jackson.module.kotlin.KotlinModule;
import com.fasterxml.jackson.module.kotlin.MissingKotlinParameterException;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import kotlin.NotImplementedError;
import kotlin.io.AccessDeniedException;
import kotlin.io.FileSystemException;
import kotlin.io.NoSuchFileException;
import org.junit.jupiter.api.Test;

/// Kotlin default constructor arguments filled in for omitted and explicit-null JSON properties
/// by the Kotlin module's value instantiator and its `KotlinFeature.NullIsSameAsDefault` feature.
///
/// A Java file-sync coordinator collects failure reports from Kotlin sync agents, which record
/// each failure as the Kotlin exception they caught: a `kotlin.io` file-system exception whose
/// `other` file and `reason` default to `null`, or the `NotImplementedError` that a `TODO()`
/// throws, whose message defaults to Kotlin's standard text. Agents leave out the properties
/// they did not set, and older agents send `null` for an unset message, so the coordinator
/// enables `NullIsSameAsDefault` and relies on the Kotlin defaults to build its digest. It
/// forwards reports with only the constructor properties on the wire, pinned by mix-ins.
class KotlinDefaultArgumentsTest {

    private static final String DEFAULT_TODO_MESSAGE = "An operation is not implemented.";

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new KotlinModule.Builder()
                    .enable(KotlinFeature.NullIsSameAsDefault)
                    .build())
            .addMixIn(FileSystemException.class, FileFailureWireFormat.class)
            .addMixIn(NotImplementedError.class, TodoWireFormat.class);

    private static final ObjectMapper STRICT_MAPPER = new ObjectMapper()
            .registerModule(new KotlinModule.Builder().build());

    /// Keeps a file-system failure to the properties its Kotlin constructor takes.
    @JsonIncludeProperties({"file", "other", "reason"})
    interface FileFailureWireFormat {
    }

    /// Keeps a `TODO()` failure to its message.
    @JsonIncludeProperties({"message"})
    interface TodoWireFormat {
    }

    /// The report one agent sends after a sync round.
    public static final class SyncReport {
        public String agent;
        public List<AccessDeniedException> denied;
        public NoSuchFileException missing;
        public List<NotImplementedError> unsupported;

        public SyncReport() {
        }

        SyncReport(String agent, List<AccessDeniedException> denied, NoSuchFileException missing,
                List<NotImplementedError> unsupported) {
            this.agent = agent;
            this.denied = denied;
            this.missing = missing;
            this.unsupported = unsupported;
        }
    }

    @Test
    void fillsOmittedPropertiesWithKotlinDefaults() throws Exception {
        String json = """
                {
                  "agent": "edge-7",
                  "denied": [
                    {"file": "/srv/share/payroll.xlsx"},
                    {"file": "/srv/share/keys", "reason": "mode 0700"}
                  ],
                  "missing": {"file": "/srv/share/a.txt", "other": "/backup/a.txt"},
                  "unsupported": [{}, {"message": "symlink sync"}]
                }
                """;

        SyncReport report = MAPPER.readValue(json, SyncReport.class);

        assertEquals("edge-7", report.agent);
        AccessDeniedException payroll = report.denied.get(0);
        assertEquals(new File("/srv/share/payroll.xlsx"), payroll.getFile());
        assertNull(payroll.getOther());
        assertNull(payroll.getReason());
        assertEquals(new File("/srv/share/keys"), report.denied.get(1).getFile());
        assertEquals("mode 0700", report.denied.get(1).getReason());
        assertEquals(new File("/backup/a.txt"), report.missing.getOther());
        assertNull(report.missing.getReason());
        assertEquals(DEFAULT_TODO_MESSAGE, report.unsupported.get(0).getMessage());
        assertEquals("symlink sync", report.unsupported.get(1).getMessage());
        assertEquals(List.of(
                "denied " + new File("/srv/share/payroll.xlsx"),
                "denied " + new File("/srv/share/keys") + ": mode 0700",
                "missing " + new File("/srv/share/a.txt") + " -> " + new File("/backup/a.txt"),
                "todo " + DEFAULT_TODO_MESSAGE,
                "todo symlink sync"), digest(report));
    }

    @Test
    void treatsExplicitNullMessageAsTheKotlinDefault() throws Exception {
        String json = """
                {"agent": "edge-legacy", "unsupported": [{"message": null}]}
                """;

        SyncReport report = MAPPER.readValue(json, SyncReport.class);

        assertEquals(DEFAULT_TODO_MESSAGE, report.unsupported.get(0).getMessage());

        MissingKotlinParameterException rejected = assertThrows(
                MissingKotlinParameterException.class,
                () -> STRICT_MAPPER.readValue(json, SyncReport.class));
        assertEquals("message", rejected.getParameter().getName());
    }

    @Test
    void forwardsReportBuiltInJavaToAnotherCoordinator() throws Exception {
        SyncReport sent = new SyncReport("hub",
                List.of(new AccessDeniedException(
                        new File("/srv/hr/salaries.csv"), null, "read-only mount")),
                new NoSuchFileException(new File("/srv/hr/old.csv"), null, null),
                List.of(new NotImplementedError()));

        String json = MAPPER.writeValueAsString(sent);

        JsonNode tree = MAPPER.readTree(json);
        assertEquals("read-only mount", tree.at("/denied/0/reason").asText());
        assertEquals(3, tree.at("/denied/0").size());
        assertTrue(tree.at("/missing/other").isNull());
        assertEquals(DEFAULT_TODO_MESSAGE, tree.at("/unsupported/0/message").asText());
        assertEquals(1, tree.at("/unsupported/0").size());

        SyncReport received = MAPPER.readValue(json, SyncReport.class);

        assertEquals("hub", received.agent);
        assertEquals(new File("/srv/hr/salaries.csv"), received.denied.get(0).getFile());
        assertEquals("read-only mount", received.denied.get(0).getReason());
        assertEquals(new File("/srv/hr/old.csv"), received.missing.getFile());
        assertNull(received.missing.getOther());
        assertEquals(digest(sent), digest(received));
    }

    private static List<String> digest(SyncReport report) {
        List<String> lines = new ArrayList<>();
        for (AccessDeniedException denied : report.denied) {
            lines.add("denied " + denied.getMessage());
        }
        if (report.missing != null) {
            lines.add("missing " + report.missing.getMessage());
        }
        for (NotImplementedError todo : report.unsupported) {
            lines.add("todo " + todo.getMessage());
        }
        return lines;
    }
}
