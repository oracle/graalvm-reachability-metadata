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

    protected void generateReportsForCoordinate(String coordinates) {
        CommandResult jacoco = runGradle(List.of("jacocoTestReport", "-Pcoordinates=" + coordinates), true);
        if (jacoco.exitCode() != 0) {
            throw new GradleException("JaCoCo report generation failed for " + coordinates + ":\n" + jacoco.stderr());
        }

        CommandResult dynamicAccess = runGradle(List.of("generateDynamicAccessReport", "-Pcoordinates=" + coordinates), true);
        if (dynamicAccess.exitCode() != 0) {
            throw new GradleException("Dynamic access report generation failed for " + coordinates + ":\n" + dynamicAccess.stderr());
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
                .resolve("library-stats-schema-v1.0.1.json");
    }

    @Internal
    protected Path getStatsFile() {
        return getStatsRoot().resolve("stats.json");
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

    protected LibraryStatsModels.VersionStats computeVersionStats(String coordinates) {
        generateReportsForCoordinate(coordinates);
        List<Path> libraryJars = listLibraryJars(coordinates);
        return LibraryStatsSupport.buildVersionStats(coordinates, libraryJars, getDynamicAccessDir(coordinates), getJacocoReport(coordinates));
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
