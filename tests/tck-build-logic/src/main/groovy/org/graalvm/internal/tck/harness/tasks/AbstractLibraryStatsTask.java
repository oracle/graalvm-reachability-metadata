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
import org.graalvm.internal.tck.stats.CoverageMeasurement;
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
        generateJacocoReport(coordinates);

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

    /// Runs the JVM tests under JaCoCo and writes the combined coverage report for one coordinate.
    protected void generateJacocoReport(String coordinates) {
        CommandResult jacoco = runGradle(List.of("jacocoCodeCoverageReport", "-Pcoordinates=" + coordinates), true);
        if (jacoco.exitCode() != 0) {
            throw new GradleException("JaCoCo report generation failed for " + coordinates + ":\n" + jacoco.stderr());
        }
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
                .resolve("jacocoCodeCoverageReport")
                .resolve("jacocoCodeCoverageReport.xml");
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
        if (!CoverageMeasurement.containsClassFiles(libraryJars)) {
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
            Path originsOutput = maybeCollectAgentOrigins(coordinates, libraryJars);
            return CoverageMeasurement.buildVersionStats(
                    coordinates,
                    libraryJars,
                    getDynamicAccessDir(coordinates),
                    getJacocoReport(coordinates),
                    CoverageMeasurement.parseAgentOrigins(originsOutput, getDynamicAccessDir(coordinates))
            );
        }
        return CoverageMeasurement.buildVersionStatsWithoutDynamicAccess(
                coordinates,
                getJacocoReport(coordinates)
        );
    }

    /// §AR-test-harness.8.1: agent-origin fallback for coordinates with a dynamic-access call site
    /// that line matching cannot decide: a line-less frame, or one in a method JaCoCo filtered out
    /// of an analyzed class. Returns null unless such a site exists; otherwise runs the JVM-only
    /// `javaTest` lane over the regular and extension suites under `native-image-agent` and returns
    /// its configuration-origin directory. `javaTest` avoids re-running and clobbering the native
    /// dynamic-access reports. A run that fails or writes no origins fails the task. Needs the
    /// coordinate's JaCoCo report.
    protected Path maybeCollectAgentOrigins(String coordinates, List<Path> libraryJars) {
        return maybeCollectAgentOrigins(coordinates, libraryJars, getDynamicAccessDir(coordinates));
    }

    /// The agent-origin fallback for call sites read from `dynamicAccessDir`.
    protected Path maybeCollectAgentOrigins(String coordinates, List<Path> libraryJars, Path dynamicAccessDir) {
        if (!CoverageMeasurement.lineMatchingUndecided(dynamicAccessDir, libraryJars, getJacocoReport(coordinates))) {
            return null;
        }
        Path originsOutput = tckExtension.getTestDir(coordinates)
                .resolve("build")
                .resolve("reports")
                .resolve("dynamic-access")
                .resolve("agent-origins");
        Path originsFile = originsOutput.resolve("reachability-metadata-origins.txt");
        getLogger().lifecycle(
                "Line matching cannot decide some dynamic-access call sites of {}; collecting native-image-agent origins.",
                coordinates
        );
        try {
            Files.createDirectories(originsOutput);
            Files.deleteIfExists(originsFile);
        } catch (IOException e) {
            throw new GradleException("Could not prepare the agent-origin output " + originsOutput + " for " + coordinates, e);
        }
        // The combined JaCoCo report measures the extension suite too, so the agent run must as well.
        CommandResult origins = runGradle(
                List.of(
                        "javaTest",
                        "-Pcoordinates=" + coordinates,
                        "-PincludeCodeCoverageSuite=true",
                        "-PdynamicAccessOriginsOutput=" + originsOutput
                ),
                true
        );
        if (origins.exitCode() != 0 || !Files.isRegularFile(originsFile)) {
            throw new GradleException("""
                    Dynamic-access coverage of %s needs native-image-agent origins for call sites that \
                    line matching cannot decide, but the agent run of `javaTest` %s.
                    Reporting without them would count those sites as uncovered; fix the JVM test run \
                    and rerun.""".formatted(
                    coordinates,
                    origins.exitCode() != 0
                            ? "failed with exit code " + origins.exitCode()
                            : "wrote no origins to " + originsFile
            ));
        }
        return originsOutput;
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
