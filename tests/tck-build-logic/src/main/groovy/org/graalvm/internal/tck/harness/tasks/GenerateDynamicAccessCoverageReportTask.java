/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness.tasks;

import org.gradle.api.GradleException;
import org.gradle.api.tasks.TaskAction;
import org.graalvm.internal.tck.stats.LibraryStatsModels;
import org.graalvm.internal.tck.stats.LibraryStatsSupport;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Generates a per-class dynamic-access coverage report for matching coordinates.
 *
 * <p>The output is written as JSON to
 * {@code metadata/<group>/<artifact>/<version>/test/build/reports/dynamic-access/dynamic-access-coverage.json}
 * and has this shape:
 *
 * <pre>{@code
 * {
 *   "coordinate": "group:artifact:version",
 *   "hasDynamicAccess": true,
 *   "totals": {
 *     "totalCalls": 10,
 *     "coveredCalls": 6
 *   },
 *   "classes": [
 *     {
 *       "className": "com.example.Foo",
 *       "sourceFile": "Foo.java",
 *       "totalCalls": 4,
 *       "coveredCalls": 2,
 *       "callSites": [
 *         {
 *           "metadataType": "reflection",
 *           "trackedApi": "java.lang.Class#forName(java.lang.String)",
 *           "frame": "com.example.Foo.load(Foo.java:10)",
 *           "line": 10,
 *           "covered": true
 *         }
 *       ]
 *     }
 *   ]
 * }
 * }</pre>
 *
 * <p>The JSON payload is emitted from {@link LibraryStatsModels.DynamicAccessCoverageReport}.
 *
 * <p>Implements §AR-test-harness.8 — the {@code generateDynamicAccessCoverageReport} task.
 */
@SuppressWarnings("unused")
public abstract class GenerateDynamicAccessCoverageReportTask extends AbstractLibraryStatsTask {

    @TaskAction
    public void generate() {
        List<String> coordinates = resolveRequestedCoordinates();
        for (String coordinate : coordinates) {
            boolean dynamicAccessAvailable = generateReportsForCoordinate(coordinate);
            requireDynamicAccessInput(coordinate, dynamicAccessAvailable);
            List<Path> libraryJars = listLibraryJars(coordinate);
            Path originsOutput = maybeCollectAgentOrigins(coordinate, libraryJars);
            LibraryStatsModels.DynamicAccessCoverageReport report = LibraryStatsSupport.buildDynamicAccessCoverageReport(
                    coordinate,
                    libraryJars,
                    getDynamicAccessDir(coordinate),
                    getJacocoReport(coordinate),
                    LibraryStatsSupport.parseAgentOrigins(originsOutput, getDynamicAccessDir(coordinate))
            );
            Path outputFile = getDynamicAccessCoverageReport(coordinate);
            LibraryStatsSupport.writeJson(outputFile, report);
            getLogger().quiet("Wrote dynamic-access coverage report for {} to {}.", coordinate, outputFile);
        }
    }

    /// Fails when the native test build did not produce the report's input.
    ///
    /// A report written over a missing input is indistinguishable from a library that
    /// reports no dynamic access at all, and consumers read a zero-call report as a
    /// property of the library (§AR-test-harness.8).
    void requireDynamicAccessInput(String coordinate, boolean dynamicAccessAvailable) {
        Path dynamicAccessDir = getDynamicAccessDir(coordinate);
        if (dynamicAccessAvailable && Files.isDirectory(dynamicAccessDir)) {
            return;
        }
        throw new GradleException("""
                Dynamic-access input for %s was not produced: %s.
                The native test build must generate it before coverage can be reported; \
                writing an empty report here would be indistinguishable from a library \
                that has no dynamic access.""".formatted(coordinate, dynamicAccessDir));
    }
}
