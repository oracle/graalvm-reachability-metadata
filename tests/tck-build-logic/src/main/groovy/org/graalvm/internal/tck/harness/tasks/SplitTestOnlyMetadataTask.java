/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness.tasks;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.graalvm.internal.tck.Coordinates;
import org.graalvm.internal.tck.model.MetadataVersionsIndexEntry;
import org.graalvm.internal.tck.utils.CoordinateUtils;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits test-only entries out of library reachability metadata into a separate
 * test-resources reachability-metadata.json.
 */
@SuppressWarnings("unused")
public class SplitTestOnlyMetadataTask extends CoordinatesAwareTask {
    private static final String REACHABILITY_METADATA_FILE = "reachability-metadata.json";
    private static final Pattern PACKAGE_PATTERN = Pattern.compile("(?m)^\\s*package\\s+([A-Za-z0-9_.]+)\\s*;?");

    private final ObjectMapper objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    @TaskAction
    public void run() {
        List<String> coordinates = resolveCoordinates();
        if (coordinates.isEmpty()) {
            getLogger().lifecycle("No matching coordinates found for splitTestOnlyMetadata. Nothing to do.");
            return;
        }

        List<String> failures = new java.util.ArrayList<>();
        for (String coordinate : coordinates) {
            if (coordinate.startsWith("samples:") || coordinate.startsWith("org.example:")) {
                continue;
            }
            try {
                splitMetadata(coordinate);
            } catch (Exception exception) {
                failures.add(coordinate + ": " + exception.getMessage());
                getLogger().error("splitTestOnlyMetadata failed for {}: {}", coordinate, exception.getMessage());
            }
        }

        if (!failures.isEmpty()) {
            throw new GradleException("splitTestOnlyMetadata failed for the following coordinates:\n - " + String.join("\n - ", failures));
        }
    }

    private void splitMetadata(String coordinate) throws IOException {
        Coordinates parsedCoordinates = Coordinates.parse(coordinate);
        Path metadataDirectory = resolveMetadataDirectory(parsedCoordinates);
        Path testsDirectory = resolveTestsDirectory(parsedCoordinates);
        Path metadataFile = metadataDirectory.resolve(REACHABILITY_METADATA_FILE);

        if (!Files.isRegularFile(metadataFile)) {
            throw new IllegalArgumentException("Cannot find metadata file for " + coordinate + ": " + metadataFile);
        }
        if (!Files.isDirectory(testsDirectory)) {
            throw new IllegalArgumentException("Cannot find tests directory for " + coordinate + ": " + testsDirectory);
        }

        Set<String> testPackages = discoverTestPackages(testsDirectory);
        if (testPackages.isEmpty()) {
            throw new IllegalStateException("Cannot determine test packages in " + testsDirectory);
        }

        ObjectNode libraryMetadata = requireObjectNode(objectMapper.readTree(metadataFile.toFile()), metadataFile);
        ObjectNode movedMetadata = objectMapper.createObjectNode();

        splitReflection(libraryMetadata, movedMetadata, testPackages);
        splitResources(libraryMetadata, movedMetadata, testPackages);

        if (movedMetadata.isEmpty()) {
            getLogger().lifecycle("No test-only reachability metadata entries found for {}", coordinate);
            return;
        }

        Path testMetadataFile = testsDirectory
                .resolve("src/test/resources/META-INF/native-image")
                .resolve(REACHABILITY_METADATA_FILE);
        ObjectNode mergedTestMetadata = mergeReachabilityMetadata(readReachabilityMetadataIfPresent(testMetadataFile), movedMetadata);

        writeJson(metadataFile, libraryMetadata);
        writeJson(testMetadataFile, mergedTestMetadata);
        getLogger().lifecycle("splitTestOnlyMetadata completed for {}", coordinate);
    }

    private Path resolveMetadataDirectory(Coordinates parsedCoordinates) throws IOException {
        Path conventional = getProject().file(CoordinateUtils.replace("metadata/$group$/$artifact$/$version$", parsedCoordinates)).toPath();
        if (Files.isDirectory(conventional)) {
            return conventional;
        }

        throw new IllegalArgumentException("Cannot find metadata directory for " + parsedCoordinates);
    }

    private Path resolveTestsDirectory(Coordinates parsedCoordinates) throws IOException {
        Path conventional = getProject().file(CoordinateUtils.replace("tests/src/$group$/$artifact$/$version$", parsedCoordinates)).toPath();
        if (Files.isDirectory(conventional)) {
            return conventional;
        }

        Path indexFile = getProject().file(CoordinateUtils.replace("metadata/$group$/$artifact$/index.json", parsedCoordinates)).toPath();
        if (!Files.isRegularFile(indexFile)) {
            throw new IllegalArgumentException("Cannot find tests directory for " + parsedCoordinates + ": " + conventional
                    + " does not exist and no index.json is present at " + indexFile);
        }

        List<MetadataVersionsIndexEntry> entries = objectMapper.readValue(indexFile.toFile(), new TypeReference<>() {});
        for (MetadataVersionsIndexEntry entry : entries) {
            if (!parsedCoordinates.version().equals(entry.metadataVersion())) {
                continue;
            }
            String testVersion = entry.testVersion();
            if (testVersion == null || testVersion.isBlank()) {
                throw new IllegalStateException("Index entry for metadata-version " + entry.metadataVersion()
                        + " in " + indexFile + " has no test-version");
            }
            return getProject().file(CoordinateUtils.replace("tests/src/$group$/$artifact$/" + testVersion, parsedCoordinates)).toPath();
        }

        throw new IllegalArgumentException("Cannot find tests directory for " + parsedCoordinates
                + ": no index.json entry with metadata-version " + parsedCoordinates.version() + " in " + indexFile);
    }

    private Set<String> discoverTestPackages(Path testsDirectory) throws IOException {
        Set<String> packages = new LinkedHashSet<>();
        Path sourceRoot = testsDirectory.resolve("src/test/java");
        if (!Files.isDirectory(sourceRoot)) {
            return packages;
        }
        try (var pathStream = Files.walk(sourceRoot)) {
            pathStream.filter(Files::isRegularFile).filter(path -> path.getFileName().toString().endsWith(".java")).forEach(path -> {
                try {
                    String fileContent = Files.readString(path, StandardCharsets.UTF_8);
                    Matcher matcher = PACKAGE_PATTERN.matcher(fileContent);
                    if (matcher.find()) {
                        packages.add(matcher.group(1));
                    }
                } catch (IOException exception) {
                    throw new RuntimeException("Cannot read test source file " + path, exception);
                }
            });
        }

        return packages;
    }

    private void splitReflection(ObjectNode source, ObjectNode moved, Set<String> testPackages) {
        JsonNode field = source.get("reflection");
        if (!(field instanceof ArrayNode sourceEntries)) {
            return;
        }

        ArrayNode keptEntries = objectMapper.createArrayNode();
        ArrayNode movedEntries = objectMapper.createArrayNode();
        for (JsonNode entry : sourceEntries) {
            JsonNode typeNode = entry.isObject() ? entry.get("type") : null;
            boolean testOnly = typeNode != null && typeNode.isTextual()
                    && testPackages.stream().anyMatch(testPackage -> referencesTestPackage(typeNode.asText(), testPackage));
            if (testOnly) {
                movedEntries.add(entry.deepCopy());
            } else {
                keptEntries.add(entry);
            }
        }

        applySplitResult(source, moved, "reflection", keptEntries, movedEntries);
    }

    private void splitResources(ObjectNode source, ObjectNode moved, Set<String> testPackages) {
        JsonNode field = source.get("resources");
        if (!(field instanceof ArrayNode sourceEntries)) {
            return;
        }

        ArrayNode keptEntries = objectMapper.createArrayNode();
        ArrayNode movedEntries = objectMapper.createArrayNode();
        for (JsonNode entry : sourceEntries) {
            JsonNode globNode = entry.isObject() ? entry.get("glob") : null;
            boolean testOnly = globNode != null && globNode.isTextual()
                    && testPackages.stream().anyMatch(testPackage -> globMatchesTestPackage(globNode.asText(), testPackage));
            if (testOnly) {
                movedEntries.add(entry.deepCopy());
            } else {
                keptEntries.add(entry);
            }
        }

        applySplitResult(source, moved, "resources", keptEntries, movedEntries);
    }

    private void applySplitResult(ObjectNode source, ObjectNode moved, String fieldName, ArrayNode keptEntries, ArrayNode movedEntries) {
        if (movedEntries.isEmpty()) {
            return;
        }
        if (keptEntries.isEmpty()) {
            source.remove(fieldName);
        } else {
            source.set(fieldName, keptEntries);
        }
        moved.set(fieldName, movedEntries);
    }

    private boolean referencesTestPackage(String value, String testPackage) {
        return value.equals(testPackage) || value.startsWith(testPackage + ".");
    }

    private boolean globMatchesTestPackage(String glob, String testPackage) {
        String normalized = glob.startsWith("/") ? glob.substring(1) : glob;
        String packageDirectory = testPackage.replace('.', '/');
        return normalized.equals(packageDirectory) || normalized.startsWith(packageDirectory + "/");
    }

    private ObjectNode readReachabilityMetadataIfPresent(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            return objectMapper.createObjectNode();
        }
        return requireObjectNode(objectMapper.readTree(file.toFile()), file);
    }

    private ObjectNode mergeReachabilityMetadata(ObjectNode existing, ObjectNode additions) {
        ObjectNode merged = existing.deepCopy();
        mergeArrayField(merged, additions, "reflection");
        mergeArrayField(merged, additions, "resources");
        return merged;
    }

    private void mergeArrayField(ObjectNode target, ObjectNode additions, String fieldName) {
        JsonNode additionsNode = additions.get(fieldName);
        if (!(additionsNode instanceof ArrayNode additionsArray)) {
            return;
        }

        ArrayNode mergedArray = target.has(fieldName) && target.get(fieldName) instanceof ArrayNode existingArray
                ? existingArray.deepCopy()
                : objectMapper.createArrayNode();
        appendDistinct(mergedArray, additionsArray);
        target.set(fieldName, mergedArray);
    }

    private void appendDistinct(ArrayNode target, ArrayNode additions) {
        for (JsonNode addition : additions) {
            boolean alreadyPresent = false;
            for (JsonNode existing : target) {
                if (existing.equals(addition)) {
                    alreadyPresent = true;
                    break;
                }
            }
            if (!alreadyPresent) {
                target.add(addition.deepCopy());
            }
        }
    }

    private ObjectNode requireObjectNode(JsonNode node, Path path) {
        if (node instanceof ObjectNode objectNode) {
            return objectNode;
        }
        throw new IllegalStateException("Expected object JSON in " + path);
    }

    private void writeJson(Path file, ObjectNode content) throws IOException {
        DefaultIndenter indenter = new DefaultIndenter("  ", "\n");
        DefaultPrettyPrinter prettyPrinter = new DefaultPrettyPrinter();
        prettyPrinter.indentObjectsWith(indenter);
        prettyPrinter.indentArraysWith(indenter);
        String json = objectMapper.writer(prettyPrinter).writeValueAsString(content);
        if (!json.endsWith("\n")) {
            json = json + "\n";
        }

        Files.createDirectories(file.getParent());
        Files.writeString(file, json, StandardCharsets.UTF_8);
    }
}
