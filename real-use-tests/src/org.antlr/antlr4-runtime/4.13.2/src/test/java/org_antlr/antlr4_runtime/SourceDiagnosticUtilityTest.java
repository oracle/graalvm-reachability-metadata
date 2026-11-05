/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_antlr.antlr4_runtime;

import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.misc.Pair;
import org.antlr.v4.runtime.misc.Utils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises ANTLR's source-diagnostic utility subsystem to format parser diagnostics.
public class SourceDiagnosticUtilityTest {

    @Test
    @Timeout(60)
    public void formatsSourceRangesForDeploymentDiagnostics() {
        String source = "route\tcheckout\n  replicas=3\n";
        DiagnosticReport report = new DiagnosticReport(source);

        Diagnostic command = report.diagnostic(Interval.of(0, 13), new Pair<>(1, 0));
        Diagnostic replicas = report.diagnostic(Interval.of(17, 26), new Pair<>(2, 2));

        assertEquals(new Pair<>(1, 0), command.location());
        assertEquals("route\\tcheckout", command.excerpt());
        assertEquals("route   checkout", report.expandTabs(Interval.of(0, 13)));
        assertEquals("replicas=3", replicas.excerpt());
        assertEquals(List.of("route\\tcheckout", "replicas=3"), report.excerpts(List.of(command, replicas)));

        Interval commandAndIndent = command.span().union(Interval.of(14, 16));
        assertEquals(Interval.of(0, 16), commandAndIndent);
        assertTrue(command.span().disjoint(replicas.span()));
        assertTrue(command.span().adjacent(Interval.of(14, 16)));
        assertEquals(Interval.of(4, 8), command.span().intersection(Interval.of(4, 8)));
        assertEquals(Interval.of(17, 21), replicas.span().differenceNotProperlyContained(Interval.of(22, 26)));
        assertEquals(2, Utils.count(source, '\n'));
    }

    private record Diagnostic(Interval span, Pair<Integer, Integer> location, String excerpt) {
    }

    private static final class DiagnosticReport {

        private final String source;

        private DiagnosticReport(String source) {
            this.source = source;
        }

        private Diagnostic diagnostic(Interval span, Pair<Integer, Integer> location) {
            return new Diagnostic(span, location, Utils.escapeWhitespace(text(span), true));
        }

        private String expandTabs(Interval span) {
            return Utils.expandTabs(text(span), 4);
        }

        private List<String> excerpts(List<Diagnostic> diagnostics) {
            return diagnostics.stream().map(Diagnostic::excerpt).toList();
        }

        private String text(Interval span) {
            return source.substring(span.a, span.b + 1);
        }
    }
}
