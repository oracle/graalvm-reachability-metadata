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

    /// Gradle property naming a copy of an earlier `dynamic-access` output.
    ///
    /// When it is set, the native listing build is skipped and only JaCoCo is
    /// correlated against the copy: the call sites depend only on the library JARs,
    /// which the tracking build preserves whole (§AR-test-harness.8).
    static final String CALL_SITES_PROPERTY = "tck.dynamicAccess.callSites";

    @TaskAction
    public void generate() {
        List<String> coordinates = resolveRequestedCoordinates();
        Path storedCallSites = storedCallSites();
        for (String coordinate : coordinates) {
            Path dynamicAccessDir;
            boolean dynamicAccessAvailable;
            if (storedCallSites == null) {
                dynamicAccessAvailable = generateReportsForCoordinate(coordinate);
                dynamicAccessDir = getDynamicAccessDir(coordinate);
            } else {
                generateJacocoReport(coordinate);
                dynamicAccessAvailable = true;
                dynamicAccessDir = storedCallSites;
            }
            requireDynamicAccessInput(coordinate, dynamicAccessAvailable, dynamicAccessDir);
            List<Path> libraryJars = listLibraryJars(coordinate);
            Path originsOutput = maybeCollectAgentOrigins(coordinate, libraryJars, dynamicAccessDir);
            LibraryStatsModels.DynamicAccessCoverageReport report = LibraryStatsSupport.buildDynamicAccessCoverageReport(
                    coordinate,
                    libraryJars,
                    dynamicAccessDir,
                    getJacocoReport(coordinate),
                    LibraryStatsSupport.parseAgentOrigins(originsOutput, dynamicAccessDir)
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
        requireDynamicAccessInput(coordinate, dynamicAccessAvailable, getDynamicAccessDir(coordinate));
    }

    void requireDynamicAccessInput(String coordinate, boolean dynamicAccessAvailable, Path dynamicAccessDir) {
        if (dynamicAccessAvailable && Files.isDirectory(dynamicAccessDir)) {
            return;
        }
        throw new GradleException("""
                Dynamic-access input for %s was not produced: %s.
                The native test build must generate it before coverage can be reported; \
                writing an empty report here would be indistinguishable from a library \
                that has no dynamic access.""".formatted(coordinate, dynamicAccessDir));
    }

    /// The stored call sites the caller passed, or `null` to list them with a native build.
    Path storedCallSites() {
        Object value = getProject().findProperty(CALL_SITES_PROPERTY);
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        return Path.of(value.toString()).toAbsolutePath();
    }
}
