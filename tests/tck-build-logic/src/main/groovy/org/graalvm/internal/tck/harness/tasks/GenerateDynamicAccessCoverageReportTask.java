/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness.tasks;

import org.gradle.api.tasks.TaskAction;
import org.graalvm.internal.tck.stats.LibraryStatsModels;
import org.graalvm.internal.tck.stats.LibraryStatsSupport;

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
 */
@SuppressWarnings("unused")
public abstract class GenerateDynamicAccessCoverageReportTask extends AbstractLibraryStatsTask {

    @TaskAction
    public void generate() {
        List<String> coordinates = resolveRequestedCoordinates();
        for (String coordinate : coordinates) {
            generateReportsForCoordinate(coordinate);
            List<Path> libraryJars = listLibraryJars(coordinate);
            LibraryStatsModels.DynamicAccessCoverageReport report = LibraryStatsSupport.buildDynamicAccessCoverageReport(
                    coordinate,
                    libraryJars,
                    getDynamicAccessDir(coordinate),
                    getJacocoReport(coordinate)
            );
            Path outputFile = getDynamicAccessCoverageReport(coordinate);
            LibraryStatsSupport.writeJson(outputFile, report);
            getLogger().quiet("Wrote dynamic-access coverage report for {} to {}.", coordinate, outputFile);
        }
    }
}
