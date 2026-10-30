/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.stats;

import org.gradle.api.GradleException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/// Measures a coordinate's coverage from its build reports: the JaCoCo XML, the native build's
/// dynamic-access call sites, and the native-image-agent configuration origins.
public final class CoverageMeasurement {

    private CoverageMeasurement() {
    }

    public static LibraryStatsModels.VersionStats buildVersionStats(
            String coordinate,
            List<Path> libraryJars,
            Path dynamicAccessDir,
            Path jacocoReport
    ) {
        return buildVersionStats(coordinate, libraryJars, dynamicAccessDir, jacocoReport, Set.of());
    }

    /// §AR-test-harness.8.1: `agentCoveredCallSites` (from `parseAgentOrigins`) decides the call
    /// sites line matching cannot; pass `Set.of()` for the line-based path.
    public static LibraryStatsModels.VersionStats buildVersionStats(
            String coordinate,
            List<Path> libraryJars,
            Path dynamicAccessDir,
            Path jacocoReport,
            Set<AgentCoveredCallSite> agentCoveredCallSites
    ) {
        Set<String> libraryClasses = loadLibraryClasses(libraryJars);
        if (libraryClasses.isEmpty()) {
            return new LibraryStatsModels.VersionStats(
                    LibraryStatsSupport.versionFromCoordinate(coordinate),
                    LibraryStatsModels.DynamicAccessStatsValue.available(LibraryStatsSupport.emptyDynamicAccessStats()),
                    LibraryStatsSupport.unavailableLibraryCoverage()
            );
        }
        JacocoReportParser.ParsedJacocoReport parsedJacocoReport = JacocoReportParser.parse(jacocoReport);
        DynamicAccessReportsParser.ParsedDynamicAccess parsedDynamicAccess =
                DynamicAccessReportsParser.parse(dynamicAccessDir, libraryClasses, parsedJacocoReport.coveredLinesBySource(),
                        parsedJacocoReport.methodNamesByClass(), agentCoveredCallSites);
        return versionStats(
                coordinate,
                LibraryStatsModels.DynamicAccessStatsValue.available(parsedDynamicAccess.dynamicAccessStats()),
                parsedJacocoReport
        );
    }

    public static LibraryStatsModels.VersionStats buildVersionStatsWithoutDynamicAccess(
            String coordinate,
            Path jacocoReport
    ) {
        JacocoReportParser.ParsedJacocoReport parsedJacocoReport = JacocoReportParser.parse(jacocoReport);
        return versionStats(
                coordinate,
                LibraryStatsModels.DynamicAccessStatsValue.notAvailable(),
                parsedJacocoReport
        );
    }

    public static ExternalDynamicAccessSummary buildExternalDynamicAccessSummary(List<Path> libraryJars, Path dynamicAccessDir) {
        Set<String> libraryClasses = loadLibraryClasses(libraryJars);
        if (libraryClasses.isEmpty()) {
            return new ExternalDynamicAccessSummary(0L, Map.of());
        }
        DynamicAccessReportsParser.ParsedDynamicAccess parsedDynamicAccess =
                DynamicAccessReportsParser.parse(dynamicAccessDir, libraryClasses, Map.of(), Map.of(), Set.of());
        return new ExternalDynamicAccessSummary(
                parsedDynamicAccess.dynamicAccessStats().totalCalls(),
                parsedDynamicAccess.dynamicAccessStats().breakdown()
        );
    }

    public static LibraryStatsModels.DynamicAccessCoverageReport buildDynamicAccessCoverageReport(
            String coordinate,
            List<Path> libraryJars,
            Path dynamicAccessDir,
            Path jacocoReport
    ) {
        return buildDynamicAccessCoverageReport(coordinate, libraryJars, dynamicAccessDir, jacocoReport, Set.of());
    }

    /// §AR-test-harness.8.1: `agentCoveredCallSites` (from `parseAgentOrigins`) decides the call
    /// sites line matching cannot; pass `Set.of()` for the line-based path.
    public static LibraryStatsModels.DynamicAccessCoverageReport buildDynamicAccessCoverageReport(
            String coordinate,
            List<Path> libraryJars,
            Path dynamicAccessDir,
            Path jacocoReport,
            Set<AgentCoveredCallSite> agentCoveredCallSites
    ) {
        Set<String> libraryClasses = loadLibraryClasses(libraryJars);
        if (libraryClasses.isEmpty()) {
            return new LibraryStatsModels.DynamicAccessCoverageReport(
                    coordinate,
                    false,
                    new LibraryStatsModels.DynamicAccessCoverageTotals(0L, 0L),
                    List.of()
            );
        }
        JacocoReportParser.ParsedJacocoReport parsedJacocoReport = JacocoReportParser.parse(jacocoReport);
        DynamicAccessReportsParser.ParsedDynamicAccess parsedDynamicAccess =
                DynamicAccessReportsParser.parse(dynamicAccessDir, libraryClasses, parsedJacocoReport.coveredLinesBySource(),
                        parsedJacocoReport.methodNamesByClass(), agentCoveredCallSites);
        return new LibraryStatsModels.DynamicAccessCoverageReport(
                coordinate,
                parsedDynamicAccess.dynamicAccessStats().totalCalls() > 0,
                new LibraryStatsModels.DynamicAccessCoverageTotals(
                        parsedDynamicAccess.dynamicAccessStats().totalCalls(),
                        parsedDynamicAccess.dynamicAccessStats().coveredCalls()
                ),
                parsedDynamicAccess.classCoverage()
        );
    }

    /// §AR-test-harness.8.1: delegates to the agent-origin matcher, which retains the statically
    /// reported call sites whose exact tracked API and caller class/method occur, in that
    /// order, on a configuration-carrying agent origin path.
    public static Set<AgentCoveredCallSite> parseAgentOrigins(Path originsOutput, Path dynamicAccessDir) {
        return AgentOriginsParser.parseAgentOrigins(originsOutput, dynamicAccessDir);
    }

    /// §AR-test-harness.8.1: the gate of the agent-origin fallback. Returns true iff the
    /// dynamic-access reports contain a library call site that agent origins can still decide: its
    /// frame is line-less, or `jacocoReport` analyzed its class but filtered its method, as for a
    /// synthetic `class$` helper, and no line-decided site shares its tracked API, class and method
    /// name. Purely data-driven — no jar bytecode scanning.
    public static boolean lineMatchingUndecided(Path dynamicAccessDir, List<Path> libraryJars, Path jacocoReport) {
        Set<String> libraryClasses = loadLibraryClasses(libraryJars);
        if (libraryClasses.isEmpty()) {
            return false;
        }
        JacocoReportParser.ParsedJacocoReport parsedJacocoReport = JacocoReportParser.parse(jacocoReport);
        DynamicAccessReportsParser.ParsedDynamicAccess parsed = DynamicAccessReportsParser.parse(dynamicAccessDir, libraryClasses,
                parsedJacocoReport.coveredLinesBySource(), parsedJacocoReport.methodNamesByClass(), Set.of());
        return parsed.lineUndecidedCalls() > 0L;
    }

    private static LibraryStatsModels.VersionStats versionStats(
            String coordinate,
            LibraryStatsModels.DynamicAccessStatsValue dynamicAccess,
            JacocoReportParser.ParsedJacocoReport parsedJacocoReport
    ) {
        return new LibraryStatsModels.VersionStats(
                LibraryStatsSupport.versionFromCoordinate(coordinate),
                dynamicAccess,
                new LibraryStatsModels.LibraryCoverage(
                        parsedJacocoReport.line(),
                        parsedJacocoReport.instruction(),
                        parsedJacocoReport.method()
                )
        );
    }

    /// Returns true if at least one of the given JARs contains a `.class` file
    /// (excluding `module-info.class`).
    public static boolean containsClassFiles(List<Path> libraryJars) {
        for (Path jarPath : libraryJars) {
            try (JarFile jarFile = new JarFile(jarPath.toFile())) {
                boolean found = jarFile.stream()
                        .map(JarEntry::getName)
                        .filter(name -> name.endsWith(".class"))
                        .anyMatch(name -> !name.equals("module-info.class"));
                if (found) {
                    return true;
                }
            } catch (IOException e) {
                throw new GradleException("Failed to read library JAR " + jarPath, e);
            }
        }
        return false;
    }

    private static Set<String> loadLibraryClasses(List<Path> libraryJars) {
        Set<String> classes = new LinkedHashSet<>();
        for (Path jarPath : libraryJars) {
            try (JarFile jarFile = new JarFile(jarPath.toFile())) {
                jarFile.stream()
                        .map(JarEntry::getName)
                        .filter(name -> name.endsWith(".class"))
                        .filter(name -> !name.equals("module-info.class"))
                        .map(CoverageMeasurement::classNameFromEntry)
                        .forEach(classes::add);
            } catch (IOException e) {
                throw new GradleException("Failed to read library JAR " + jarPath, e);
            }
        }
        return classes;
    }

    private static String classNameFromEntry(String entryName) {
        String normalized = entryName;
        if (entryName.startsWith("META-INF/versions/")) {
            String[] segments = entryName.split("/", 4);
            normalized = segments[3];
        }
        return normalized.substring(0, normalized.length() - ".class".length()).replace('/', '.');
    }

    public record AgentCoveredCallSite(String trackedApi, String className, String methodName) {
    }

    public record ExternalDynamicAccessSummary(
            long totalCalls,
            Map<String, LibraryStatsModels.DynamicAccessBreakdown> breakdown
    ) {
    }
}
