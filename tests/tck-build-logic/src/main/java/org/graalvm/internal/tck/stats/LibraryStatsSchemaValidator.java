/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.stats;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.gradle.api.GradleException;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
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
 * Validates {@code stats/stats.json} against a versioned schema and repository metadata layout.
 */
public final class LibraryStatsSchemaValidator {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int RATIO_SCALE = 6;
    private static final int EXPECTED_RATIO_SCALE = 12;
    private static final BigDecimal RATIO_TOLERANCE = new BigDecimal("0.000001");
    private static final Pattern MISSING_METADATA_VERSION_ENTRY_PATTERN =
            Pattern.compile("^Missing metadata-version entry for ([^\\s]+) in .+$");

    private static final JsonSchemaFactory JSON_SCHEMA_FACTORY = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);

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
        try {
            schema = JSON_SCHEMA_FACTORY.getSchema(schemaFile.toUri());
        } catch (Exception e) {
            throw new GradleException("Failed to load library stats schema from " + schemaFile, e);
        }

        Map<String, Set<String>> expectedArtifacts = collectExpectedArtifacts(metadataRoot);
        List<String> failures = new ArrayList<>();
        final Path[] statsFileHolder = new Path[1];

        if (Files.exists(statsRoot)) {
            try (Stream<Path> files = Files.walk(statsRoot)) {
                files.filter(Files::isRegularFile)
                        .sorted(Comparator.comparing(path -> path.toAbsolutePath().toString()))
                        .forEach(file -> {
                            Path candidate = validateStatsPath(file, statsRoot, failures);
                            if (candidate == null) {
                                return;
                            }
                            if (statsFileHolder[0] != null) {
                                failures.add("Duplicate stats file: " + statsFileHolder[0] + " and " + candidate);
                            } else {
                                statsFileHolder[0] = candidate;
                            }
                        });
            } catch (IOException e) {
                throw new GradleException("Failed to traverse stats directory " + statsRoot, e);
            }
        }

        Path statsFile = statsFileHolder[0];
        if (!expectedArtifacts.isEmpty() && statsFile == null) {
            failures.add("Missing stats file: " + statsRoot.resolve("stats.json"));
        }

        if (statsFile != null) {
            validateAgainstSchema(statsFile, schema, failures);
            validateEntriesAlignment(statsFile, expectedArtifacts, failures);
            validateRatioConsistency(statsFile, failures);
            validateNormalizedContent(statsFile, failures);
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

    private static Path validateStatsPath(
            Path file,
            Path statsRoot,
            List<String> failures
    ) {
        Path relative = statsRoot.relativize(file);
        if (isSchemaFile(relative)) {
            return null;
        }

        if (!file.getFileName().toString().endsWith(".json")) {
            return null;
        }

        if (relative.getNameCount() == 1 && "stats.json".equals(relative.getName(0).toString())) {
            return file;
        }

        failures.add("Unexpected JSON file under stats root: " + file + " (expected stats/stats.json or stats/schemas/*.json)");
        return null;
    }

    private static boolean isSchemaFile(Path relativePath) {
        return relativePath.getNameCount() >= 2 && "schemas".equals(relativePath.getName(0).toString());
    }

    private static Map<String, Set<String>> collectExpectedArtifacts(Path metadataRoot) {
        Map<String, Set<String>> expected = new TreeMap<>();
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
                        String artifact = groupId + ":" + artifactId;
                        Set<String> metadataVersions = new TreeSet<>();
                        try (Stream<Path> metadataVersionDirs = Files.list(artifactDir)) {
                            metadataVersionDirs
                                    .filter(Files::isDirectory)
                                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                                    .forEach(metadataVersionDir -> metadataVersions.add(metadataVersionDir.getFileName().toString()));
                        } catch (IOException e) {
                            throw new GradleException("Failed to list metadata versions under " + artifactDir, e);
                        }
                        expected.put(artifact, metadataVersions);
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

    private static void validateEntriesAlignment(
            Path statsFile,
            Map<String, Set<String>> expectedArtifacts,
            List<String> failures
    ) {
        JsonNode json;
        try {
            json = OBJECT_MAPPER.readTree(statsFile.toFile());
        } catch (IOException e) {
            failures.add("Failed to parse stats JSON file " + statsFile + ": " + e.getMessage());
            return;
        }

        JsonNode entries = json.get("entries");
        if (entries == null || !entries.isObject()) {
            failures.add("Stats file is missing object field 'entries': " + statsFile);
            return;
        }

        Map<String, JsonNode> actualArtifacts = new LinkedHashMap<>();
        entries.fields().forEachRemaining(entry -> actualArtifacts.put(entry.getKey(), entry.getValue()));

        for (String expectedArtifact : expectedArtifacts.keySet()) {
            if (!actualArtifacts.containsKey(expectedArtifact)) {
                Set<String> expectedArtifactMetadataVersions = expectedArtifacts.get(expectedArtifact);
                for (String expectedMetadataVersion : expectedArtifactMetadataVersions) {
                    failures.add("Missing metadata-version entry for " + expectedArtifact + ":" + expectedMetadataVersion + " in " + statsFile);
                }
            }
        }

        for (Map.Entry<String, JsonNode> entry : actualArtifacts.entrySet()) {
            String artifact = entry.getKey();
            Set<String> expectedArtifactMetadataVersions = expectedArtifacts.get(artifact);
            if (expectedArtifactMetadataVersions == null) {
                failures.add("Orphan artifact entry in stats file without matching metadata directory: " + artifact);
                continue;
            }
            validateArtifactEntry(statsFile, artifact, entry.getValue(), expectedArtifactMetadataVersions, failures);
        }
    }

    private static void validateArtifactEntry(
            Path statsFile,
            String artifact,
            JsonNode artifactEntry,
            Set<String> expectedMetadataVersionsForArtifact,
            List<String> failures
    ) {
        JsonNode metadataVersions = artifactEntry.get("metadataVersions");
        if (metadataVersions == null || !metadataVersions.isObject()) {
            failures.add("Stats file is missing object field 'metadataVersions' for " + artifact + " in " + statsFile);
            return;
        }

        Set<String> actualMetadataVersions = new TreeSet<>();
        metadataVersions.fieldNames().forEachRemaining(actualMetadataVersions::add);

        for (String expectedMetadataVersion : expectedMetadataVersionsForArtifact) {
            if (!actualMetadataVersions.contains(expectedMetadataVersion)) {
                failures.add("Missing metadata-version entry for " + artifact + ":" + expectedMetadataVersion + " in " + statsFile);
                continue;
            }
            JsonNode metadataVersionEntry = metadataVersions.get(expectedMetadataVersion);
            validateMetadataVersionEntry(statsFile, artifact, expectedMetadataVersion, metadataVersionEntry, failures);
        }

        for (String actualMetadataVersion : actualMetadataVersions) {
            if (!expectedMetadataVersionsForArtifact.contains(actualMetadataVersion)) {
                failures.add("Orphan metadata-version entry in stats file for " + artifact + ":" + actualMetadataVersion);
            }
        }
    }

    private static void validateMetadataVersionEntry(
            Path statsFile,
            String artifact,
            String metadataVersion,
            JsonNode metadataVersionEntry,
            List<String> failures
    ) {
        if (metadataVersionEntry == null || !metadataVersionEntry.isObject()) {
            failures.add("Stats file is missing object entry for " + artifact + ":" + metadataVersion + " in " + statsFile);
            return;
        }

        JsonNode versions = metadataVersionEntry.get("versions");
        if (versions == null || !versions.isArray()) {
            failures.add("Stats file is missing array field 'versions' for " + artifact + ":" + metadataVersion + " in " + statsFile);
            return;
        }

        if (versions.isEmpty()) {
            failures.add("Missing version report entries in stats file for " + artifact + ":" + metadataVersion + " in " + statsFile);
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
            LibraryStatsModels.LibraryStats libraryStats = LibraryStatsSupport.loadStats(statsFile);
            String normalized = LibraryStatsSupport.toNormalizedPrettyJsonWithTrailingNewline(libraryStats);
            String actual = Files.readString(statsFile, StandardCharsets.UTF_8);
            if (!actual.equals(normalized)) {
                failures.add("Stats file is not normalized and sorted: " + statsFile
                        + ". Run './gradlew generateLibraryStats -Pcoordinates=all'.");
            }
        } catch (Exception e) {
            failures.add("Failed to validate normalized sorting for " + statsFile + ": " + e.getMessage());
        }
    }

    private static void validateRatioConsistency(Path statsFile, List<String> failures) {
        LibraryStatsModels.LibraryStats libraryStats;
        try {
            libraryStats = LibraryStatsSupport.loadStats(statsFile);
        } catch (Exception e) {
            failures.add("Failed to validate ratio consistency for " + statsFile + ": " + e.getMessage());
            return;
        }

        libraryStats.entries().forEach((artifact, artifactStats) ->
                artifactStats.metadataVersions().forEach((metadataVersion, metadataVersionStats) ->
                        metadataVersionStats.versions().forEach(versionStats ->
                                validateVersionRatios(artifact, metadataVersion, versionStats, failures)
                        )
                )
        );
    }

    private static void validateVersionRatios(
            String artifact,
            String metadataVersion,
            LibraryStatsModels.VersionStats versionStats,
            List<String> failures
    ) {
        String locationPrefix = artifact + ":" + metadataVersion + ":" + versionStats.version();

        LibraryStatsModels.DynamicAccessStatsValue dynamicAccess = versionStats.dynamicAccess();
        if (dynamicAccess != null && dynamicAccess.isAvailable()) {
            validateRatio(
                    locationPrefix + ":dynamicAccess.coverageRatio",
                    dynamicAccess.coverageRatio(),
                    dynamicAccess.coveredCalls(),
                    dynamicAccess.totalCalls(),
                    failures
            );
            dynamicAccess.breakdown().forEach((reportType, breakdown) -> validateRatio(
                    locationPrefix + ":dynamicAccess.breakdown." + reportType + ".coverageRatio",
                    breakdown.coverageRatio(),
                    breakdown.coveredCalls(),
                    breakdown.totalCalls(),
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
        validateRatio(
                locationPrefix + ":libraryCoverage." + metricName + ".ratio",
                metric.ratio(),
                metric.covered(),
                metric.total(),
                failures
        );
    }

    private static void validateRatio(
            String location,
            BigDecimal actualRatio,
            long covered,
            long total,
            List<String> failures
    ) {
        BigDecimal expectedRatio = expectedRatio(covered, total);
        BigDecimal difference = actualRatio.subtract(expectedRatio).abs();
        if (difference.compareTo(RATIO_TOLERANCE) > 0) {
            failures.add("Ratio mismatch at " + location + ": expected " + expectedRatio
                    + " from covered=" + covered + ", total=" + total
                    + " but found " + actualRatio + " (tolerance " + RATIO_TOLERANCE + ")");
        }
    }

    private static BigDecimal expectedRatio(long covered, long total) {
        if (total == 0L) {
            return BigDecimal.ZERO.setScale(RATIO_SCALE, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(covered)
                .divide(BigDecimal.valueOf(total), EXPECTED_RATIO_SCALE, RoundingMode.HALF_UP);
    }

}
