/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.stats;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.gradle.api.GradleException;
import org.graalvm.internal.tck.model.MetadataVersionsIndexEntry;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Validates exploded local stats files against a versioned schema and repository metadata layout.
 */
public final class LibraryStatsSchemaValidator {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int RATIO_SCALE = 6;
    private static final int EXPECTED_RATIO_SCALE = 12;
    private static final BigDecimal RATIO_TOLERANCE = new BigDecimal("0.000001");
    private static final Pattern MISSING_METADATA_VERSION_ENTRY_PATTERN =
            Pattern.compile("^Missing metadata-version entry for ([^\\s]+) in .+$");

    private static final JsonSchemaFactory JSON_SCHEMA_FACTORY = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
    private static final TypeReference<List<MetadataVersionsIndexEntry>> INDEX_ENTRIES_TYPE = new TypeReference<>() {
    };

    private LibraryStatsSchemaValidator() {
    }

    public static void validateOrThrow(Path jsonFile, Path schemaFile) {
        try {
            JsonSchema schema = JSON_SCHEMA_FACTORY.getSchema(schemaFile.toUri());
            JsonNode json = OBJECT_MAPPER.readTree(jsonFile.toFile());
            Set<ValidationMessage> errors = schema.validate(json);
            if (!errors.isEmpty()) {
                String message = errors.stream()
                        .map(ValidationMessage::getMessage)
                        .sorted()
                        .collect(Collectors.joining(System.lineSeparator()));
                throw new GradleException("Library stats schema validation failed for " + jsonFile + ":" + System.lineSeparator() + message);
            }
        } catch (IOException e) {
            throw new GradleException("Failed to read library stats JSON or schema", e);
        }
    }

    public static void validateRepositoryStatsOrThrow(Path metadataRoot, Path statsRoot, Path schemaFile) {
        JsonSchema schema;
        JsonSchema runMetricsSchema;
        try {
            schema = JSON_SCHEMA_FACTORY.getSchema(schemaFile.toUri());
            runMetricsSchema = JSON_SCHEMA_FACTORY.getSchema(statsRoot.resolve("schemas").resolve("run_metrics_output_schema.json").toUri());
        } catch (Exception e) {
            throw new GradleException("Failed to load library stats schema from " + schemaFile, e);
        }

        Map<StatsLocation, Path> expectedStatsFiles = collectExpectedStatsFiles(metadataRoot, statsRoot);
        Map<String, Set<String>> testedVersionsByArtifact = collectTestedVersionsByArtifact(metadataRoot);
        Map<StatsLocation, Path> actualStatsFiles = new TreeMap<>(Comparator.comparing(StatsLocation::sortKey));
        List<String> failures = new ArrayList<>();

        if (Files.exists(statsRoot)) {
            try (Stream<Path> files = Files.walk(statsRoot)) {
                files.filter(Files::isRegularFile)
                        .sorted(Comparator.comparing(path -> path.toAbsolutePath().toString()))
                        .forEach(file -> {
                            StatsLocation location = validateStatsPath(
                                    file,
                                    statsRoot,
                                    runMetricsSchema,
                                    testedVersionsByArtifact,
                                    failures
                            );
                            if (location == null) {
                                return;
                            }
                            Path previous = actualStatsFiles.put(location, file);
                            if (previous != null) {
                                failures.add("Duplicate stats file: " + previous + " and " + file);
                            }
                        });
            } catch (IOException e) {
                throw new GradleException("Failed to traverse stats directory " + statsRoot, e);
            }
        }

        for (Map.Entry<StatsLocation, Path> entry : expectedStatsFiles.entrySet()) {
            if (!actualStatsFiles.containsKey(entry.getKey())) {
                failures.add("Missing metadata-version entry for " + entry.getKey().coordinate() + " in " + entry.getValue());
            }
        }

        for (Map.Entry<StatsLocation, Path> entry : actualStatsFiles.entrySet()) {
            StatsLocation location = entry.getKey();
            Path file = entry.getValue();
            if (!expectedStatsFiles.containsKey(location)) {
                failures.add("Orphan stats file without matching metadata directory: " + location.coordinate());
                continue;
            }

            validateAgainstSchema(file, schema, failures);
            validateMetadataVersionEntry(file, location, failures);
            validateRatioConsistency(file, location, failures);
            validateNormalizedContent(file, failures);
        }

        if (!failures.isEmpty()) {
            String message = failures.stream().sorted().collect(Collectors.joining(System.lineSeparator()));
            String remediation = buildRemediation(message);
            if (!remediation.isEmpty()) {
                message = message + System.lineSeparator() + System.lineSeparator() + remediation;
            }
            throw new GradleException("Library stats repository validation failed:" + System.lineSeparator() + message);
        }
    }

    private static String buildRemediation(String message) {
        Set<String> missingCoordinates = message.lines()
                .map(MISSING_METADATA_VERSION_ENTRY_PATTERN::matcher)
                .filter(Matcher::matches)
                .map(matcher -> matcher.group(1))
                .collect(Collectors.toCollection(TreeSet::new));

        if (missingCoordinates.isEmpty()) {
            return "";
        }

        return missingCoordinates.stream()
                .map(coordinate -> "Add the missing library stats entry with: ./gradlew generateLibraryStats -Pcoordinates=" + coordinate)
                .collect(Collectors.joining(System.lineSeparator()));
    }

    private static StatsLocation validateStatsPath(
            Path file,
            Path statsRoot,
            JsonSchema runMetricsSchema,
            Map<String, Set<String>> testedVersionsByArtifact,
            List<String> failures
    ) {
        Path relative = statsRoot.relativize(file);
        if (isSchemaFile(relative)) {
            return null;
        }

        if (!file.getFileName().toString().endsWith(".json")) {
            return null;
        }

        if (isExecutionMetricsFile(relative)) {
            validateExecutionMetricsFile(file, relative, runMetricsSchema, testedVersionsByArtifact, failures);
            return null;
        }

        if (relative.getNameCount() == 4 && "stats.json".equals(relative.getName(3).toString())) {
            return new StatsLocation(
                    relative.getName(0).toString(),
                    relative.getName(1).toString(),
                    relative.getName(2).toString()
            );
        }

        failures.add("Unexpected JSON file under stats root: " + file
                + " (expected stats/<groupId>/<artifactId>/<metadataVersion>/stats.json, "
                + "stats/<groupId>/<artifactId>/<metadataVersion>/execution-metrics.json, or stats/schemas/*.json)");
        return null;
    }

    private static boolean isSchemaFile(Path relativePath) {
        return relativePath.getNameCount() >= 2 && "schemas".equals(relativePath.getName(0).toString());
    }

    private static boolean isExecutionMetricsFile(Path relativePath) {
        return relativePath.getNameCount() == 4
                && "execution-metrics.json".equals(relativePath.getName(3).toString());
    }

    private static void validateExecutionMetricsFile(
            Path file,
            Path relativePath,
            JsonSchema runMetricsSchema,
            Map<String, Set<String>> testedVersionsByArtifact,
            List<String> failures
    ) {
        validateAgainstSchema(file, runMetricsSchema, failures);
        try {
            JsonNode json = OBJECT_MAPPER.readTree(file.toFile());
            if (!json.isObject()) {
                failures.add("Execution metrics file must be an object keyed by <task-type>:<date>: " + file);
                return;
            }

            String expectedLibrary = relativePath.getName(0) + ":" + relativePath.getName(1) + ":" + relativePath.getName(2);
            json.fields().forEachRemaining(entry -> {
                JsonNode libraryNode = entry.getValue().get("library");
                if (libraryNode == null || !expectedLibrary.equals(libraryNode.asText())) {
                    failures.add("Execution metrics library mismatch in " + file + " at key " + entry.getKey()
                            + ": expected " + expectedLibrary);
                    return;
                }
                validateMetricCoordinateExists(
                        file,
                        entry.getKey(),
                        "library",
                        libraryNode.asText(),
                        testedVersionsByArtifact,
                        failures
                );
                validateStatsVersionMatchesLibrary(file, entry.getKey(), entry.getValue(), libraryNode.asText(), "stats", failures);

                JsonNode previousLibraryNode = entry.getValue().get("previous_library");
                if (previousLibraryNode != null && previousLibraryNode.isTextual()) {
                    validateMetricCoordinateExists(
                            file,
                            entry.getKey(),
                            "previous_library",
                            previousLibraryNode.asText(),
                            testedVersionsByArtifact,
                            failures
                    );
                    validateStatsVersionMatchesLibrary(
                            file,
                            entry.getKey(),
                            entry.getValue(),
                            previousLibraryNode.asText(),
                            "previous_library_stats",
                            failures
                    );
                }
            });
        } catch (IOException e) {
            failures.add("Failed to parse execution metrics JSON file " + file + ": " + e.getMessage());
        }
    }

    private static void validateMetricCoordinateExists(
            Path file,
            String entryKey,
            String fieldName,
            String coordinate,
            Map<String, Set<String>> testedVersionsByArtifact,
            List<String> failures
    ) {
        MavenCoordinate parsed = MavenCoordinate.parse(coordinate);
        if (parsed == null) {
            failures.add("Invalid execution metrics " + fieldName + " coordinate in " + file
                    + " at key " + entryKey + ": " + coordinate);
            return;
        }

        Set<String> testedVersions = testedVersionsByArtifact.get(parsed.artifactCoordinate());
        if (testedVersions == null) {
            failures.add("Execution metrics " + fieldName + " has no matching metadata index in " + file
                    + " at key " + entryKey + ": " + coordinate);
            return;
        }

        if (!testedVersions.contains(parsed.version())) {
            failures.add("Execution metrics " + fieldName + " version is not listed in metadata index tested-versions in "
                    + file + " at key " + entryKey + ": " + coordinate);
        }
    }

    private static void validateStatsVersionMatchesLibrary(
            Path file,
            String entryKey,
            JsonNode runMetrics,
            String coordinate,
            String statsFieldName,
            List<String> failures
    ) {
        MavenCoordinate parsed = MavenCoordinate.parse(coordinate);
        if (parsed == null) {
            return;
        }

        JsonNode stats = runMetrics.get(statsFieldName);
        if (stats == null || !stats.isObject()) {
            return;
        }
        JsonNode version = stats.get("version");
        if (version != null && version.isTextual() && !parsed.version().equals(version.asText())) {
            failures.add("Execution metrics " + statsFieldName + ".version mismatch in " + file
                    + " at key " + entryKey + ": expected " + parsed.version()
                    + " from " + coordinate + " but found " + version.asText());
        }
    }

    private static Map<StatsLocation, Path> collectExpectedStatsFiles(Path metadataRoot, Path statsRoot) {
        Map<StatsLocation, Path> expected = new TreeMap<>(Comparator.comparing(StatsLocation::sortKey));
        if (!Files.isDirectory(metadataRoot)) {
            return expected;
        }

        try (Stream<Path> groupDirs = Files.list(metadataRoot)) {
            groupDirs.filter(Files::isDirectory).forEach(groupDir -> {
                String groupId = groupDir.getFileName().toString();
                try (Stream<Path> artifactDirs = Files.list(groupDir)) {
                    artifactDirs.filter(Files::isDirectory).forEach(artifactDir -> {
                        if (!Files.isRegularFile(artifactDir.resolve("index.json"))) {
                            return;
                        }
                        String artifactId = artifactDir.getFileName().toString();
                        try (Stream<Path> metadataVersionDirs = Files.list(artifactDir)) {
                            metadataVersionDirs
                                    .filter(Files::isDirectory)
                                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                                    .forEach(metadataVersionDir -> {
                                        String metadataVersion = metadataVersionDir.getFileName().toString();
                                        StatsLocation location = new StatsLocation(groupId, artifactId, metadataVersion);
                                        expected.put(
                                                location,
                                                LibraryStatsSupport.repositoryStatsFile(
                                                        statsRoot,
                                                        groupId,
                                                        artifactId,
                                                        metadataVersion
                                                )
                                        );
                                    });
                        } catch (IOException e) {
                            throw new GradleException("Failed to list metadata versions under " + artifactDir, e);
                        }
                    });
                } catch (IOException e) {
                    throw new GradleException("Failed to list metadata artifacts under " + groupDir, e);
                }
            });
        } catch (IOException e) {
            throw new GradleException("Failed to list metadata groups under " + metadataRoot, e);
        }

        return expected;
    }

    private static Map<String, Set<String>> collectTestedVersionsByArtifact(Path metadataRoot) {
        Map<String, Set<String>> testedVersionsByArtifact = new TreeMap<>();
        if (!Files.isDirectory(metadataRoot)) {
            return testedVersionsByArtifact;
        }

        try (Stream<Path> indexFiles = Files.find(metadataRoot, 3, (path, attrs) -> {
            if (!attrs.isRegularFile()) {
                return false;
            }
            Path relative = metadataRoot.relativize(path);
            return relative.getNameCount() == 3 && "index.json".equals(relative.getName(2).toString());
        })) {
            for (Path indexFile : indexFiles.sorted().toList()) {
                Path relative = metadataRoot.relativize(indexFile);
                String artifactCoordinate = relative.getName(0) + ":" + relative.getName(1);
                Set<String> testedVersions = testedVersionsByArtifact.computeIfAbsent(
                        artifactCoordinate,
                        ignored -> new TreeSet<>()
                );
                try {
                    List<MetadataVersionsIndexEntry> entries = OBJECT_MAPPER.readValue(indexFile.toFile(), INDEX_ENTRIES_TYPE);
                    for (MetadataVersionsIndexEntry entry : entries) {
                        if (entry != null && entry.testedVersions() != null) {
                            testedVersions.addAll(entry.testedVersions());
                        }
                    }
                } catch (IOException e) {
                    throw new GradleException("Failed to read metadata index file " + indexFile, e);
                }
            }
        } catch (IOException e) {
            throw new GradleException("Failed to traverse metadata root " + metadataRoot, e);
        }

        return testedVersionsByArtifact;
    }

    private static void validateMetadataVersionEntry(
            Path statsFile,
            StatsLocation location,
            List<String> failures
    ) {
        LibraryStatsModels.MetadataVersionStats metadataVersionStats;
        try {
            metadataVersionStats = LibraryStatsSupport.loadMetadataVersionStats(statsFile);
        } catch (Exception e) {
            failures.add("Failed to parse stats JSON file " + statsFile + ": " + e.getMessage());
            return;
        }

        if (metadataVersionStats.versions() == null || metadataVersionStats.versions().isEmpty()) {
            failures.add("Missing version report entries in stats file for " + location.coordinate() + " in " + statsFile);
        }
    }

    private static void validateAgainstSchema(Path file, JsonSchema schema, List<String> failures) {
        try {
            JsonNode json = OBJECT_MAPPER.readTree(file.toFile());
            Set<ValidationMessage> errors = new LinkedHashSet<>(schema.validate(json));
            if (!errors.isEmpty()) {
                String joined = errors.stream()
                        .map(ValidationMessage::getMessage)
                        .sorted()
                        .collect(Collectors.joining("; "));
                failures.add("Schema validation failed for " + file + ": " + joined);
            }
        } catch (IOException e) {
            failures.add("Failed to parse stats JSON file " + file + ": " + e.getMessage());
        }
    }

    private static void validateNormalizedContent(Path statsFile, List<String> failures) {
        try {
            LibraryStatsModels.MetadataVersionStats metadataVersionStats = LibraryStatsSupport.loadMetadataVersionStats(statsFile);
            String normalized = LibraryStatsSupport.toNormalizedPrettyJsonWithTrailingNewline(metadataVersionStats);
            String actual = Files.readString(statsFile, StandardCharsets.UTF_8);
            if (!actual.equals(normalized)) {
                failures.add("Stats file is not normalized and sorted: " + statsFile
                        + ". Run './gradlew generateLibraryStats -Pcoordinates=all'.");
            }
        } catch (Exception e) {
            failures.add("Failed to validate normalized sorting for " + statsFile + ": " + e.getMessage());
        }
    }

    private static void validateRatioConsistency(Path statsFile, StatsLocation location, List<String> failures) {
        LibraryStatsModels.MetadataVersionStats metadataVersionStats;
        try {
            metadataVersionStats = LibraryStatsSupport.loadMetadataVersionStats(statsFile);
        } catch (Exception e) {
            failures.add("Failed to validate ratio consistency for " + statsFile + ": " + e.getMessage());
            return;
        }

        for (LibraryStatsModels.VersionStats versionStats : metadataVersionStats.versions()) {
            validateVersionRatios(location, versionStats, failures);
        }
    }

    private static void validateVersionRatios(
            StatsLocation location,
            LibraryStatsModels.VersionStats versionStats,
            List<String> failures
    ) {
        String locationPrefix = location.coordinate() + ":" + versionStats.version();

        LibraryStatsModels.DynamicAccessStatsValue dynamicAccess = versionStats.dynamicAccess();
        if (dynamicAccess != null && dynamicAccess.isAvailable()) {
            validateRatio(
                    locationPrefix + ":dynamicAccess.coverageRatio",
                    dynamicAccess.coverageRatio(),
                    dynamicAccess.coveredCalls(),
                    dynamicAccess.totalCalls(),
                    true,
                    failures
            );
            dynamicAccess.breakdown().forEach((reportType, breakdown) -> validateRatio(
                    locationPrefix + ":dynamicAccess.breakdown." + reportType + ".coverageRatio",
                    breakdown.coverageRatio(),
                    breakdown.coveredCalls(),
                    breakdown.totalCalls(),
                    true,
                    failures
            ));
        }

        validateCoverageMetric(locationPrefix, "line", versionStats.libraryCoverage().line(), failures);
        validateCoverageMetric(locationPrefix, "instruction", versionStats.libraryCoverage().instruction(), failures);
        validateCoverageMetric(locationPrefix, "method", versionStats.libraryCoverage().method(), failures);
    }

    private static void validateCoverageMetric(
            String locationPrefix,
            String metricName,
            LibraryStatsModels.CoverageMetricValue metric,
            List<String> failures
    ) {
        if (metric == null || !metric.isAvailable()) {
            return;
        }
        long expectedTotal = metric.covered() + metric.missed();
        if (metric.total() != expectedTotal) {
            failures.add("Inconsistent totals at " + locationPrefix + ":libraryCoverage." + metricName
                    + ".total: expected " + expectedTotal + " from covered+missed but found " + metric.total());
        }
        boolean zeroTotalMetric = metric.total() == 0L;
        validateRatio(
                locationPrefix + ":libraryCoverage." + metricName + ".ratio",
                metric.ratio(),
                metric.covered(),
                metric.total(),
                zeroTotalMetric,
                failures
        );
    }

    private static void validateRatio(
            String location,
            BigDecimal actualRatio,
            long covered,
            long total,
            boolean treatZeroTotalAsFullCoverage,
            List<String> failures
    ) {
        BigDecimal expectedRatio = expectedRatio(covered, total, treatZeroTotalAsFullCoverage);
        BigDecimal difference = actualRatio.subtract(expectedRatio).abs();
        if (difference.compareTo(RATIO_TOLERANCE) > 0) {
            failures.add("Ratio mismatch at " + location + ": expected " + expectedRatio
                    + " from covered=" + covered + ", total=" + total
                    + " but found " + actualRatio + " (tolerance " + RATIO_TOLERANCE + ")");
        }
    }

    private static BigDecimal expectedRatio(long covered, long total, boolean treatZeroTotalAsFullCoverage) {
        if (total == 0L) {
            if (treatZeroTotalAsFullCoverage) {
                return BigDecimal.ONE.setScale(RATIO_SCALE, RoundingMode.HALF_UP);
            }
            return BigDecimal.ZERO.setScale(RATIO_SCALE, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(covered)
                .divide(BigDecimal.valueOf(total), EXPECTED_RATIO_SCALE, RoundingMode.HALF_UP);
    }

    private record StatsLocation(
            String groupId,
            String artifactId,
            String metadataVersion
    ) {
        private String coordinate() {
            return groupId + ":" + artifactId + ":" + metadataVersion;
        }

        private String sortKey() {
            return coordinate();
        }
    }

    private record MavenCoordinate(
            String groupId,
            String artifactId,
            String version
    ) {
        private static MavenCoordinate parse(String coordinate) {
            if (coordinate == null) {
                return null;
            }
            String[] parts = coordinate.split(":", -1);
            if (parts.length != 3 || parts[0].isBlank() || parts[1].isBlank() || parts[2].isBlank()) {
                return null;
            }
            return new MavenCoordinate(parts[0], parts[1], parts[2]);
        }

        private String artifactCoordinate() {
            return groupId + ":" + artifactId;
        }
    }
}
