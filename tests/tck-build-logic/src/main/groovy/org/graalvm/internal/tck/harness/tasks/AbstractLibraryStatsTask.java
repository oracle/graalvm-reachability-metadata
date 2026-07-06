/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness.tasks;

import org.gradle.api.GradleException;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.options.Option;
import org.gradle.process.ExecOperations;
import org.gradle.process.ExecResult;
import org.gradle.process.ExecSpec;
import org.graalvm.internal.tck.Coordinates;
import org.graalvm.internal.tck.stats.LibraryStatsModels;
import org.graalvm.internal.tck.stats.LibraryStatsSchemaValidator;
import org.graalvm.internal.tck.stats.LibraryStatsSupport;
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Shared support for stats tasks that operate on repository-supported coordinates.
 */
public abstract class AbstractLibraryStatsTask extends CoordinatesAwareTask {

    @Input
    @Optional
    public abstract Property<@NotNull String> getCoordinates();

    @Inject
    public abstract ExecOperations getExecOperations();

    @Option(option = "coordinates", description = "Coordinate filter (group[:artifact[:version]] or k/n fractional batch)")
    public void setCoordinatesOption(String value) {
        getCoordinates().set(value);
    }

    protected String effectiveCoordinateFilter() {
        String optionValue = getCoordinates().getOrNull();
        if (optionValue != null) {
            return optionValue;
        }
        Object property = getProject().findProperty("coordinates");
        return property == null ? "" : property.toString();
    }

    protected List<String> resolveRequestedCoordinates() {
        List<String> override = getCoordinatesOverride().getOrElse(List.of());
        if (!override.isEmpty()) {
            return override.stream().distinct().filter(coord -> !coord.startsWith("samples:")).toList();
        }

        String filter = effectiveCoordinateFilter();
        List<String> resolved = new ArrayList<>();
        if (filter == null || filter.isBlank()) {
            resolved.addAll(computeMatchingCoordinates(""));
        } else {
            for (String singleFilter : filter.split("\\s+")) {
                if (!singleFilter.isEmpty()) {
                    resolved.addAll(computeMatchingCoordinates(singleFilter));
                }
            }
        }

        List<String> deduplicated = resolved.stream().distinct().filter(coord -> !coord.startsWith("samples:")).toList();
        if (deduplicated.isEmpty()) {
            throw new GradleException("No matching coordinates found for library stats generation.");
        }
        return deduplicated;
    }

    protected Set<StatsLocation> metadataVersionsWithCompleteCoverage(Collection<String> coordinates) {
        Map<StatsLocation, Set<String>> selectedVersionsByMetadataVersion = new LinkedHashMap<>();
        for (String coordinate : coordinates) {
            StatsLocation location = resolveStatsLocation(coordinate);
            String version = LibraryStatsSupport.versionFromCoordinate(coordinate);
            selectedVersionsByMetadataVersion.computeIfAbsent(location, ignored -> new LinkedHashSet<>()).add(version);
        }

        Set<StatsLocation> fullyCoveredMetadataVersions = new LinkedHashSet<>();
        for (Map.Entry<StatsLocation, Set<String>> entry : selectedVersionsByMetadataVersion.entrySet()) {
            StatsLocation location = entry.getKey();
            List<String> allCoordinatesForArtifact = tckExtension.getMatchingCoordinates(location.artifact());
            Set<String> allVersionsForMetadataVersion = allCoordinatesForArtifact.stream()
                    .filter(candidate -> resolveStatsLocation(candidate).equals(location))
                    .map(LibraryStatsSupport::versionFromCoordinate)
                    .collect(LinkedHashSet::new, Set::add, Set::addAll);
            if (entry.getValue().equals(allVersionsForMetadataVersion)) {
                fullyCoveredMetadataVersions.add(location);
            }
        }
        return fullyCoveredMetadataVersions;
    }

    protected List<Path> listLibraryJars(String coordinates) {
        CommandResult result = runGradle(List.of("--quiet", "listLibraryJars", "-Pcoordinates=" + coordinates), false);
        if (result.exitCode() != 0) {
            throw new GradleException("Listing library JARs failed for " + coordinates + ":\n" + result.stderr());
        }

        List<Path> jars = result.stdout().lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .map(Path::of)
                .filter(Files::exists)
                .toList();

        if (jars.isEmpty()) {
            throw new GradleException("No library JARs were reported for " + coordinates);
        }
        return jars;
    }

    protected boolean generateReportsForCoordinate(String coordinates) {
        CommandResult jacoco = runGradle(List.of("jacocoTestReport", "-Pcoordinates=" + coordinates), true);
        if (jacoco.exitCode() != 0) {
            throw new GradleException("JaCoCo report generation failed for " + coordinates + ":\n" + jacoco.stderr());
        }

        CommandResult dynamicAccess = runGradle(List.of("generateDynamicAccessReport", "-Pcoordinates=" + coordinates), true);
        if (dynamicAccess.exitCode() != 0) {
            getLogger().warn(
                    "Dynamic access report generation failed for {} with exit code {}. Writing dynamicAccess as N/A.",
                    coordinates,
                    dynamicAccess.exitCode()
            );
            return false;
        }
        return true;
    }

    @Internal
    protected Path getMetadataRoot() {
        return tckExtension.getRepoRoot().get().getAsFile().toPath().resolve("metadata");
    }

    @Internal
    protected Path getStatsRoot() {
        return tckExtension.getRepoRoot().get().getAsFile().toPath().resolve("stats");
    }

    @Internal
    protected Path getStatsSchemaFile() {
        return getStatsRoot()
                .resolve("schemas")
                .resolve("library-stats-schema-v1.0.2.json");
    }

    @Internal
    protected Path getStatsFile(StatsLocation location) {
        return LibraryStatsSupport.repositoryStatsFile(
                getStatsRoot(),
                location.groupId(),
                location.artifactId(),
                location.metadataVersion()
        );
    }

    protected StatsLocation resolveStatsLocation(String coordinate) {
        Coordinates parsed = Coordinates.parse(coordinate);
        String artifact = parsed.group() + ":" + parsed.artifact();
        Path metadataDir = tckExtension.getMetadataDir(coordinate);
        String metadataVersion = metadataDir.getFileName().toString();
        return new StatsLocation(parsed.group(), parsed.artifact(), artifact, metadataVersion);
    }

    protected Path getJacocoReport(String coordinates) {
        return tckExtension.getTestDir(coordinates)
                .resolve("build")
                .resolve("reports")
                .resolve("jacoco")
                .resolve("test")
                .resolve("jacocoTestReport.xml");
    }

    protected Path getDynamicAccessDir(String coordinates) {
        return tckExtension.getTestDir(coordinates)
                .resolve("build")
                .resolve("native")
                .resolve("nativeTestCompile")
                .resolve("dynamic-access");
    }

    protected Path getDynamicAccessCoverageReport(String coordinates) {
        return tckExtension.getTestDir(coordinates)
                .resolve("build")
                .resolve("reports")
                .resolve("dynamic-access")
                .resolve("dynamic-access-coverage.json");
    }

    protected LibraryStatsModels.VersionStats computeVersionStats(String coordinates) {
        List<Path> libraryJars = listLibraryJars(coordinates);

        // When library JARs contain no bytecode (no .class files), JaCoCo has nothing
        // to instrument and produces no report. Keep coverage as N/A, but model
        // dynamic access as an empty fully-covered set like other empty fallbacks.
        if (!LibraryStatsSupport.containsClassFiles(libraryJars)) {
            getLogger().warn(
                    "Library JARs for {} contain no bytecode. Writing empty dynamic access stats and N/A coverage.",
                    coordinates
            );
            return new LibraryStatsModels.VersionStats(
                    LibraryStatsSupport.versionFromCoordinate(coordinates),
                    LibraryStatsModels.DynamicAccessStatsValue.available(LibraryStatsSupport.emptyDynamicAccessStats()),
                    LibraryStatsSupport.unavailableLibraryCoverage()
            );
        }

        boolean dynamicAccessAvailable = generateReportsForCoordinate(coordinates);
        if (dynamicAccessAvailable) {
            Path tracePath = maybeCollectAgentTrace(coordinates, libraryJars);
            return LibraryStatsSupport.buildVersionStats(
                    coordinates,
                    libraryJars,
                    getDynamicAccessDir(coordinates),
                    getJacocoReport(coordinates),
                    LibraryStatsSupport.parseAgentTrace(tracePath)
            );
        }
        return LibraryStatsSupport.buildVersionStatsWithoutDynamicAccess(
                coordinates,
                getJacocoReport(coordinates)
        );
    }

    /// §TCK-test-harness.8: agent-trace fallback for coordinates whose dynamic-access frames carry
    /// no line numbers. Returns null (today's behaviour) unless line-based matching is impossible;
    /// otherwise runs the JVM-only `javaTest` lane under `native-image-agent` and returns the trace
    /// path. `javaTest` is used rather than the full `test` lane so the native build (and the
    /// dynamic-access reports it produced) is not re-run and clobbered. A failed or missing trace
    /// degrades gracefully to null — it never fails the stats task.
    protected Path maybeCollectAgentTrace(String coordinates, List<Path> libraryJars) {
        if (!LibraryStatsSupport.lineMatchingImpossible(getDynamicAccessDir(coordinates), libraryJars)) {
            return null;
        }
        Path tracePath = tckExtension.getTestDir(coordinates)
                .resolve("build")
                .resolve("reports")
                .resolve("dynamic-access")
                .resolve("agent-trace.json");
        getLogger().lifecycle(
                "Dynamic-access frames for {} carry no line numbers; collecting native-image-agent trace.",
                coordinates
        );
        try {
            // The agent does not create the trace-output parent directory itself.
            Files.createDirectories(tracePath.getParent());
            Files.deleteIfExists(tracePath);
        } catch (IOException e) {
            getLogger().warn("Could not prepare agent-trace output for {}: {}", coordinates, e.getMessage());
            return null;
        }
        CommandResult trace = runGradle(
                List.of("javaTest", "-Pcoordinates=" + coordinates, "-PdynamicAccessTraceOutput=" + tracePath),
                true
        );
        if (trace.exitCode() != 0 || !Files.isRegularFile(tracePath)) {
            getLogger().warn(
                    "Agent-trace collection for {} failed (exit code {}); falling back to line-based coverage only.",
                    coordinates,
                    trace.exitCode()
            );
            return null;
        }
        return tracePath;
    }

    protected void validateCommittedStatsFiles() {
        LibraryStatsSchemaValidator.validateRepositoryStatsOrThrow(getMetadataRoot(), getStatsRoot(), getStatsSchemaFile());
    }

    protected CommandResult runGradle(List<String> arguments, boolean streamOutput) {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        List<String> command = new ArrayList<>();
        command.add(tckExtension.getRepoRoot().get().getAsFile().toPath().resolve("gradlew").toString());
        command.addAll(arguments);

        ExecResult execResult = getExecOperations().exec((ExecSpec spec) -> {
            spec.commandLine(command);
            spec.workingDir(tckExtension.getRepoRoot().get().getAsFile());
            spec.setIgnoreExitValue(true);
            if (streamOutput) {
                spec.setStandardOutput(new TeeOutputStream(stdout, System.out));
                spec.setErrorOutput(new TeeOutputStream(stderr, System.err));
            } else {
                spec.setStandardOutput(stdout);
                spec.setErrorOutput(stderr);
            }
        });

        return new CommandResult(
                execResult.getExitValue(),
                stdout.toString(StandardCharsets.UTF_8),
                stderr.toString(StandardCharsets.UTF_8)
        );
    }

    protected record CommandResult(int exitCode, String stdout, String stderr) {
    }

    protected record StatsLocation(String groupId, String artifactId, String artifact, String metadataVersion) {
    }
}
