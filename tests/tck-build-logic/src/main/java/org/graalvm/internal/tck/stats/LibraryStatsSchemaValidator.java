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
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Validates {@code stats/stats.json} against a versioned schema and repository metadata layout.
 */
public final class LibraryStatsSchemaValidator {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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

        Map<String, ExpectedArtifact> expectedArtifacts = collectExpectedArtifacts(metadataRoot);
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
            validateNormalizedContent(statsFile, failures);
        }

        if (!failures.isEmpty()) {
            String message = failures.stream().sorted().collect(Collectors.joining(System.lineSeparator()));
            throw new GradleException("Library stats repository validation failed:" + System.lineSeparator() + message);
        }
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

    private static Map<String, ExpectedArtifact> collectExpectedArtifacts(Path metadataRoot) {
        Map<String, ExpectedArtifact> expected = new TreeMap<>();
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
                        expected.put(artifact, new ExpectedArtifact(artifactId, metadataVersions));
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
            Map<String, ExpectedArtifact> expectedArtifacts,
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
                failures.add("Missing artifact entry in stats file for " + expectedArtifact + " (" + statsFile + ")");
            }
        }

        for (Map.Entry<String, JsonNode> entry : actualArtifacts.entrySet()) {
            String artifact = entry.getKey();
            ExpectedArtifact expectedArtifact = expectedArtifacts.get(artifact);
            if (expectedArtifact == null) {
                failures.add("Orphan artifact entry in stats file without matching metadata directory: " + artifact);
                continue;
            }
            validateArtifactEntry(statsFile, artifact, entry.getValue(), expectedArtifact, failures);
        }
    }

    private static void validateArtifactEntry(
            Path statsFile,
            String artifact,
            JsonNode artifactEntry,
            ExpectedArtifact expectedArtifact,
            List<String> failures
    ) {
        String artifactId = textOrNull(artifactEntry.get("artifactId"));
        if (!expectedArtifact.artifactId().equals(artifactId)) {
            failures.add("Stats file artifactId mismatch for " + artifact + " in " + statsFile + ": expected '"
                    + expectedArtifact.artifactId() + "' but found '" + artifactId + "'.");
        }

        JsonNode metadataVersions = artifactEntry.get("metadataVersions");
        if (metadataVersions == null || !metadataVersions.isObject()) {
            failures.add("Stats file is missing object field 'metadataVersions' for " + artifact + " in " + statsFile);
            return;
        }

        Set<String> actualMetadataVersions = new TreeSet<>();
        metadataVersions.fieldNames().forEachRemaining(actualMetadataVersions::add);

        for (String expectedMetadataVersion : expectedArtifact.metadataVersions()) {
            if (!actualMetadataVersions.contains(expectedMetadataVersion)) {
                failures.add("Missing metadata-version entry for " + artifact + ":" + expectedMetadataVersion + " in " + statsFile);
            }
        }

        for (String actualMetadataVersion : actualMetadataVersions) {
            if (!expectedArtifact.metadataVersions().contains(actualMetadataVersion)) {
                failures.add("Orphan metadata-version entry in stats file for " + artifact + ":" + actualMetadataVersion);
            }
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

    private static String textOrNull(JsonNode node) {
        return node != null && node.isTextual() ? node.asText() : null;
    }

    private record ExpectedArtifact(String artifactId, Set<String> metadataVersions) {
    }
}
