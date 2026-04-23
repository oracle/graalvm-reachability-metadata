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

/**
 * Splits test-only entries out of library reachability metadata into a separate
 * test-resources reachability-metadata.json.
 */
@SuppressWarnings("unused")
public class SplitTestOnlyMetadataTask extends CoordinatesAwareTask {
    private static final String REACHABILITY_METADATA_FILE = "reachability-metadata.json";

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
        if (metadataDirectory == null) {
            getLogger().lifecycle(
                    "Skipping {}: library version {} does not have its own metadata directory. "
                            + "splitTestOnlyMetadata only runs for versions that have their own metadata.",
                    coordinate,
                    parsedCoordinates.version()
            );
            return;
        }
        Path testsDirectory = resolveTestsDirectoryForMetadataVersion(parsedCoordinates);
        Path metadataFile = metadataDirectory.resolve(REACHABILITY_METADATA_FILE);

        if (!Files.isRegularFile(metadataFile)) {
            getLogger().lifecycle("Skipping {}: no {} found in {}", coordinate, REACHABILITY_METADATA_FILE, metadataDirectory);
            return;
        }
        if (!Files.isDirectory(testsDirectory)) {
            throw new IllegalArgumentException("Cannot find tests directory for " + coordinate + ": " + testsDirectory);
        }

        Set<String> testPackages = discoverTestPackages(testsDirectory);
        Set<String> testResources = discoverTestResources(testsDirectory);

        ObjectNode libraryMetadata = requireObjectNode(objectMapper.readTree(metadataFile.toFile()), metadataFile);
        ObjectNode movedMetadata = objectMapper.createObjectNode();
        Path testMetadataFile = testsDirectory
                .resolve("src/test/resources/META-INF/native-image")
                .resolve(REACHABILITY_METADATA_FILE);
        ObjectNode existingTestMetadata = readReachabilityMetadataIfPresent(testMetadataFile);
        ObjectNode retainedTestMetadata = retainTestOnlyMetadata(existingTestMetadata, testPackages, testResources);

        splitReflection(libraryMetadata, movedMetadata, testPackages);
        splitResources(libraryMetadata, movedMetadata, testResources);

        ObjectNode finalTestMetadata = mergeReachabilityMetadata(retainedTestMetadata, movedMetadata);

        if (finalTestMetadata.isEmpty()) {
            deleteFileIfPresent(testMetadataFile);
            getLogger().lifecycle("No test-only reachability metadata entries found for {}", coordinate);
            return;
        }

        writeJson(metadataFile, libraryMetadata);
        writeJson(testMetadataFile, finalTestMetadata);
        getLogger().lifecycle("splitTestOnlyMetadata completed for {}", coordinate);
    }

    private Path resolveMetadataDirectory(Coordinates parsedCoordinates) {
        Path conventional = getProject().file(CoordinateUtils.replace("metadata/$group$/$artifact$/$version$", parsedCoordinates)).toPath();
        if (Files.isDirectory(conventional)) {
            return conventional;
        }
        return null;
    }

    private Path resolveTestsDirectoryForMetadataVersion(Coordinates parsedCoordinates) throws IOException {
        Path conventional = getProject().file(CoordinateUtils.replace("tests/src/$group$/$artifact$/$version$", parsedCoordinates)).toPath();
        if (Files.isDirectory(conventional)) {
            return conventional;
        }

        Path indexFile = getProject().file(CoordinateUtils.replace("metadata/$group$/$artifact$/index.json", parsedCoordinates)).toPath();
        if (!Files.isRegularFile(indexFile)) {
            throw new IllegalArgumentException("Cannot find tests directory for " + parsedCoordinates + ": " + conventional
                    + " does not exist and no index.json is present at " + indexFile);
        }

        List<MetadataVersionsIndexEntry> entries = readIndexEntries(indexFile);
        for (MetadataVersionsIndexEntry entry : entries) {
            if (!parsedCoordinates.version().equals(entry.metadataVersion())) {
                continue;
            }
            return resolveTestsDirectoryForEntry(parsedCoordinates, entry, indexFile);
        }

        throw new IllegalArgumentException("Cannot find tests directory for " + parsedCoordinates
                + ": no index.json entry with metadata-version " + parsedCoordinates.version() + " in " + indexFile);
    }

    private Path resolveTestsDirectoryForEntry(Coordinates parsedCoordinates, MetadataVersionsIndexEntry entry, Path indexFile) {
        String testVersion = entry.testVersion();
        if (testVersion == null || testVersion.isBlank()) {
            testVersion = entry.metadataVersion();
        }
        if (testVersion == null || testVersion.isBlank()) {
            throw new IllegalStateException("Index entry for metadata-version " + entry.metadataVersion()
                    + " in " + indexFile + " has no test-version or metadata-version");
        }
        return getProject().file(CoordinateUtils.replace("tests/src/$group$/$artifact$/" + testVersion, parsedCoordinates)).toPath();
    }

    private List<MetadataVersionsIndexEntry> readIndexEntries(Path indexFile) throws IOException {
        return objectMapper.readValue(indexFile.toFile(), new TypeReference<>() {});
    }

    private Set<String> discoverTestPackages(Path testsDirectory) throws IOException {
        Set<String> packages = new LinkedHashSet<>();
        discoverTestPackages(testsDirectory.resolve("src/test/java"), packages);
        discoverTestPackages(testsDirectory.resolve("src/test/kotlin"), packages);
        discoverTestPackages(testsDirectory.resolve("src/test/scala"), packages);
        return packages;
    }

    private void discoverTestPackages(Path sourceRoot, Set<String> packages) throws IOException {
        if (!Files.isDirectory(sourceRoot)) {
            return;
        }
        try (var pathStream = Files.walk(sourceRoot)) {
            pathStream.filter(Files::isRegularFile)
                    .forEach(path -> {
                        Path relativeParent = sourceRoot.relativize(path).getParent();
                        if (relativeParent == null) {
                            return;
                        }
                        String packageName = relativeParent.toString().replace('/', '.').replace('\\', '.');
                        if (!packageName.isBlank()) {
                            packages.add(packageName);
                        }
                    });
        }
    }

    private Set<String> discoverTestResources(Path testsDirectory) throws IOException {
        Set<String> resources = new LinkedHashSet<>();
        Path resourceRoot = testsDirectory.resolve("src/test/resources");
        if (!Files.isDirectory(resourceRoot)) {
            return resources;
        }
        try (var pathStream = Files.walk(resourceRoot)) {
            pathStream.filter(Files::isRegularFile)
                    .map(resourceRoot::relativize)
                    .map(Path::toString)
                    .map(this::normalizeResourcePath)
                    .forEach(resources::add);
        }

        return resources;
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
            boolean testOnly = isTestOnlyReflectionType(typeNode, testPackages);
            if (testOnly) {
                movedEntries.add(entry.deepCopy());
            } else {
                keptEntries.add(entry);
            }
        }

        applySplitResult(source, moved, "reflection", keptEntries, movedEntries);
    }

    private boolean isTestOnlyReflectionType(JsonNode typeNode, Set<String> testPackages) {
        if (typeNode == null) {
            return false;
        }
        if (typeNode.isTextual()) {
            return testPackages.stream().anyMatch(testPackage -> referencesTestPackage(typeNode.asText(), testPackage));
        }
        if (typeNode.isObject()) {
            JsonNode proxyNode = typeNode.get("proxy");
            if (!(proxyNode instanceof ArrayNode proxyArray) || proxyArray.isEmpty()) {
                return false;
            }
            boolean referencesAnyTestPackage = false;
            for (JsonNode proxyType : proxyArray) {
                if (!proxyType.isTextual()) {
                    return false;
                }
                if (testPackages.stream().anyMatch(testPackage -> referencesTestPackage(proxyType.asText(), testPackage))) {
                    referencesAnyTestPackage = true;
                }
            }
            return referencesAnyTestPackage;
        }
        return false;
    }

    private void splitResources(ObjectNode source, ObjectNode moved, Set<String> testResources) {
        JsonNode field = source.get("resources");
        if (!(field instanceof ArrayNode sourceEntries)) {
            return;
        }

        ArrayNode keptEntries = objectMapper.createArrayNode();
        ArrayNode movedEntries = objectMapper.createArrayNode();
        for (JsonNode entry : sourceEntries) {
            JsonNode globNode = entry.isObject() ? entry.get("glob") : null;
            boolean testOnly = globNode != null && globNode.isTextual()
                    && testResources.contains(normalizeResourcePath(globNode.asText()));
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

    private String normalizeResourcePath(String path) {
        String normalized = path.replace('\\', '/');
        if (normalized.startsWith("/")) {
            return normalized.substring(1);
        }
        return normalized;
    }

    private ObjectNode retainTestOnlyMetadata(ObjectNode existingMetadata, Set<String> testPackages, Set<String> testResources) {
        ObjectNode retainedMetadata = objectMapper.createObjectNode();
        splitReflection(existingMetadata.deepCopy(), retainedMetadata, testPackages);
        splitResources(existingMetadata.deepCopy(), retainedMetadata, testResources);
        return retainedMetadata;
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

    private void deleteFileIfPresent(Path file) throws IOException {
        if (Files.isRegularFile(file)) {
            Files.delete(file);
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
