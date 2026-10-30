/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_api_core;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.apache.maven.api.Constants;
import org.apache.maven.api.ProtoSession;
import org.apache.maven.api.services.BuilderProblem;
import org.apache.maven.api.services.BuilderProblem.Severity;
import org.apache.maven.api.services.ProblemCollector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ProblemCollectorTest {
    @Test
    void reportsAndOrdersBuildProblemsBySeverity() {
        ProblemCollector<BuilderProblem> collector = ProblemCollector.create(null);
        BuilderProblem warning = new ValidationProblem(Severity.WARNING, "Unused property");
        BuilderProblem error = new ValidationProblem(Severity.ERROR, "Missing artifact identifier");
        BuilderProblem fatal = new ValidationProblem(Severity.FATAL, "Invalid project model");

        assertFalse(collector.hasWarningProblems());
        assertTrue(collector.reportProblem(warning));
        assertTrue(collector.hasWarningProblems());
        assertFalse(collector.hasErrorProblems());
        assertTrue(collector.reportProblem(error));
        assertTrue(collector.hasErrorProblems());
        assertFalse(collector.hasFatalProblems());
        assertTrue(collector.reportProblem(fatal));
        assertTrue(collector.hasFatalProblems());
        assertFalse(collector.problemsOverflow());
        assertEquals(3, collector.totalProblemsReported());
        assertEquals(1, collector.problemsReportedFor(Severity.WARNING));
        assertEquals(List.of(fatal, error, warning), collector.problems().toList());
        assertEquals(List.of(error), collector.problems(Severity.ERROR).toList());
    }

    @Test
    void honorsSessionLimitWhileRetainingMoreSevereProblems() {
        ProtoSession session = ProtoSession.newBuilder()
                .withUserProperties(Map.of(Constants.MAVEN_BUILDER_MAX_PROBLEMS, "2"))
                .withSystemProperties(Map.of())
                .withTopDirectory(Path.of("project"))
                .build();
        ProblemCollector<BuilderProblem> collector = ProblemCollector.create(session);
        BuilderProblem first = new ValidationProblem(Severity.WARNING, "First warning");
        BuilderProblem second = new ValidationProblem(Severity.WARNING, "Second warning");
        BuilderProblem error = new ValidationProblem(Severity.ERROR, "Missing parent model");

        assertTrue(collector.reportProblem(first));
        assertTrue(collector.reportProblem(second));
        assertTrue(collector.reportProblem(error));
        assertFalse(collector.reportProblem(new ValidationProblem(Severity.WARNING, "Extra warning")));
        assertTrue(collector.problemsOverflow());
        assertEquals(4, collector.totalProblemsReported());
        assertEquals(3, collector.problemsReportedFor(Severity.WARNING));
        assertEquals(List.of(error, second), collector.problems().toList());
    }

    private record ValidationProblem(Severity severity, String message) implements BuilderProblem {
        @Override
        public String getSource() {
            return "pom.xml";
        }

        @Override
        public int getLineNumber() {
            return 1;
        }

        @Override
        public int getColumnNumber() {
            return 1;
        }

        @Override
        public String getLocation() {
            return "pom.xml:1:1";
        }

        @Override
        public Exception getException() {
            return null;
        }

        @Override
        public String getMessage() {
            return message;
        }

        @Override
        public Severity getSeverity() {
            return severity;
        }
    }
}
