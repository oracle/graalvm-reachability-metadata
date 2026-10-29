/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness.tasks;

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
import org.graalvm.internal.tck.utils.MetadataGenerationUtils;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.TaskAction;

import javax.lang.model.SourceVersion;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Splits test-only entries out of library reachability metadata into a separate
 * test-resources reachability-metadata.json.
 * <p>
 * Implements §AR-test-harness.5 — the {@code splitTestOnlyMetadata} helper that keeps test-only
 * entries out of the shipped metadata (§FS-metadata, §FS-repository-functional-spec.5.1).
 */
@SuppressWarnings("unused")
public class SplitTestOnlyMetadataTask extends CoordinatesAwareTask {
    private static final String REACHABILITY_METADATA_FILE = "reachability-metadata.json";
    private static final String CLASS_FILE_SUFFIX = ".class";
    private static final String GLOB_METACHARACTERS = "*?{[";

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
        Path metadataDirectory = MetadataCoordinateSupport.resolveMetadataDirectory(
                getProject(),
                objectMapper,
                parsedCoordinates
        );
        if (metadataDirectory == null) {
            getLogger().lifecycle(
                    "Skipping {}: no metadata bucket supports library version {}.",
                    coordinate,
                    parsedCoordinates.version()
            );
            return;
        }
        MetadataVersionsIndexEntry indexEntry = resolveIndexEntry(parsedCoordinates);
        Path testsDirectory = resolveTestsDirectoryForMetadataVersion(parsedCoordinates, indexEntry);
        Path metadataFile = metadataDirectory.resolve(REACHABILITY_METADATA_FILE);

        if (!Files.isRegularFile(metadataFile)) {
            getLogger().lifecycle("Skipping {}: no {} found in {}", coordinate, REACHABILITY_METADATA_FILE, metadataDirectory);
            return;
        }
        if (!Files.isDirectory(testsDirectory)) {
            throw new IllegalArgumentException("Cannot find tests directory for " + coordinate + ": " + testsDirectory);
        }

        Set<String> testPackages = MetadataGenerationUtils.discoverTestOnlyMetadataPackages(testsDirectory);
        Set<String> testResources = discoverTestResources(testsDirectory);
        List<String> allowedPackages = indexEntry.allowedPackages() == null ? List.of() : indexEntry.allowedPackages();

        ObjectNode libraryMetadata = requireObjectNode(objectMapper.readTree(metadataFile.toFile()), metadataFile);
        ObjectNode movedMetadata = objectMapper.createObjectNode();
        Path testMetadataFile = testsDirectory
                .resolve("src/test/resources/META-INF/native-image")
                .resolve(REACHABILITY_METADATA_FILE);
        ObjectNode existingTestMetadata = readReachabilityMetadataIfPresent(testMetadataFile);
        ObjectNode retainedTestMetadata = retainTestOnlyMetadata(
                existingTestMetadata,
                testPackages,
                testResources,
                allowedPackages
        );

        splitReflection(libraryMetadata, movedMetadata, testPackages);
        splitSerialization(libraryMetadata, movedMetadata, testPackages);
        splitResources(libraryMetadata, movedMetadata, testPackages, testResources, allowedPackages);

        ObjectNode finalTestMetadata = mergeReachabilityMetadata(retainedTestMetadata, movedMetadata);

        writeJson(metadataFile, libraryMetadata);

        if (finalTestMetadata.isEmpty()) {
            deleteFileIfPresent(testMetadataFile);
            getLogger().lifecycle("No test-only reachability metadata entries found for {}", coordinate);
            return;
        }

        writeJson(testMetadataFile, finalTestMetadata);
        getLogger().lifecycle("splitTestOnlyMetadata completed for {}", coordinate);
    }

    /// The `index.json` entry whose metadata-version or tested-versions holds the coordinate.
    private MetadataVersionsIndexEntry resolveIndexEntry(Coordinates parsedCoordinates) throws IOException {
        Path indexFile = getProject().file(CoordinateUtils.replace("metadata/$group$/$artifact$/index.json", parsedCoordinates)).toPath();
        if (!Files.isRegularFile(indexFile)) {
            throw new IllegalArgumentException("Cannot find index.json for " + parsedCoordinates + " at " + indexFile);
        }

        List<MetadataVersionsIndexEntry> entries = MetadataCoordinateSupport.readIndexEntries(objectMapper, indexFile);
        for (MetadataVersionsIndexEntry entry : entries) {
            boolean matchesMetadataVersion = parsedCoordinates.version().equals(entry.metadataVersion());
            boolean matchesTestedVersion = entry.testedVersions() != null
                    && entry.testedVersions().contains(parsedCoordinates.version());
            if (matchesMetadataVersion || matchesTestedVersion) {
                return entry;
            }
        }

        throw new IllegalArgumentException("No index.json entry with metadata-version or tested-version "
                + parsedCoordinates.version() + " in " + indexFile);
    }

    private Path resolveTestsDirectoryForMetadataVersion(Coordinates parsedCoordinates, MetadataVersionsIndexEntry entry) {
        Path conventional = getProject().file(CoordinateUtils.replace("tests/src/$group$/$artifact$/$version$", parsedCoordinates)).toPath();
        if (Files.isDirectory(conventional)) {
            return conventional;
        }

        String testVersion = entry.testVersion();
        if (testVersion == null || testVersion.isBlank()) {
            testVersion = entry.metadataVersion();
        }
        if (testVersion == null || testVersion.isBlank()) {
            throw new IllegalStateException("Index entry for " + parsedCoordinates + " has no test-version or metadata-version");
        }
        return getProject().file(CoordinateUtils.replace("tests/src/$group$/$artifact$/" + testVersion, parsedCoordinates)).toPath();
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
        splitTypeKeyedArray(source, moved, "reflection", testPackages);
    }

    private void splitSerialization(ObjectNode source, ObjectNode moved, Set<String> testPackages) {
        splitTypeKeyedArray(source, moved, "serialization", testPackages);
    }

    private void splitTypeKeyedArray(ObjectNode source, ObjectNode moved, String fieldName, Set<String> testPackages) {
        JsonNode field = source.get(fieldName);
        if (!(field instanceof ArrayNode sourceEntries)) {
            return;
        }

        ArrayNode keptEntries = objectMapper.createArrayNode();
        ArrayNode movedEntries = objectMapper.createArrayNode();
        for (JsonNode entry : sourceEntries) {
            if (isTestOnlyEntry(entry, testPackages)) {
                movedEntries.add(entry.deepCopy());
            } else {
                keptEntries.add(entry);
            }
        }

        applySplitResult(source, moved, fieldName, keptEntries, movedEntries);
    }

    private boolean isTestOnlyEntry(JsonNode entry, Set<String> testPackages) {
        if (entry == null || !entry.isObject()) {
            return false;
        }
        if (typeReferencesTestPackage(entry.get("type"), testPackages)) {
            return true;
        }
        JsonNode condition = entry.get("condition");
        if (condition != null && condition.isObject()) {
            JsonNode typeReached = condition.get("typeReached");
            if (typeReached != null && typeReached.isTextual()
                    && referencesAnyPackage(typeReached.asText(), testPackages)) {
                return true;
            }
        }
        return false;
    }

    private boolean typeReferencesTestPackage(JsonNode typeNode, Set<String> testPackages) {
        if (typeNode == null) {
            return false;
        }
        if (typeNode.isTextual()) {
            return referencesAnyPackage(typeNode.asText(), testPackages);
        }
        if (typeNode.isObject()) {
            JsonNode lambdaNode = typeNode.get("lambda");
            if (lambdaNode != null) {
                return lambdaReferencesTestPackage(lambdaNode, testPackages);
            }
            JsonNode proxyNode = typeNode.get("proxy");
            if (!(proxyNode instanceof ArrayNode proxyArray) || proxyArray.isEmpty()) {
                return false;
            }
            boolean any = false;
            for (JsonNode proxyType : proxyArray) {
                if (!proxyType.isTextual()) {
                    return false;
                }
                if (referencesAnyPackage(proxyType.asText(), testPackages)) {
                    any = true;
                }
            }
            return any;
        }
        return false;
    }

    /// A lambda is test-only when it is declared in a test class or implements a test-package
    /// interface (§FS-metadata.2).
    private boolean lambdaReferencesTestPackage(JsonNode lambdaNode, Set<String> testPackages) {
        JsonNode declaringClass = lambdaNode.get("declaringClass");
        if (declaringClass == null || !declaringClass.isTextual()
                || !(lambdaNode.get("interfaces") instanceof ArrayNode interfaces)) {
            throw new IllegalArgumentException("Lambda type needs a declaringClass and interfaces: " + lambdaNode);
        }
        if (referencesAnyPackage(declaringClass.asText(), testPackages)) {
            return true;
        }
        for (JsonNode lambdaInterface : interfaces) {
            if (referencesAnyPackage(lambdaInterface.asText(), testPackages)) {
                return true;
            }
        }
        return false;
    }

    private boolean referencesAnyPackage(String value, Collection<String> packages) {
        return packages.stream().anyMatch(packageName -> referencesPackage(value, packageName));
    }

    private void splitResources(
            ObjectNode source,
            ObjectNode moved,
            Set<String> testPackages,
            Set<String> testResources,
            List<String> allowedPackages
    ) {
        JsonNode field = source.get("resources");
        if (!(field instanceof ArrayNode sourceEntries)) {
            return;
        }

        ArrayNode keptEntries = objectMapper.createArrayNode();
        ArrayNode movedEntries = objectMapper.createArrayNode();
        for (JsonNode entry : sourceEntries) {
            if (globIsTestResource(entry, testPackages, testResources, allowedPackages)
                    || isTestOnlyEntry(entry, testPackages)) {
                movedEntries.add(entry.deepCopy());
            } else {
                keptEntries.add(entry);
            }
        }

        applySplitResult(source, moved, "resources", keptEntries, movedEntries);
    }

    /// A resource glob is a test resource when it names a file under the test project's
    /// `src/test/resources` or matches only class files of test-package classes
    /// (§FS-repository-functional-spec.5.1).
    private boolean globIsTestResource(
            JsonNode entry,
            Set<String> testPackages,
            Set<String> testResources,
            List<String> allowedPackages
    ) {
        JsonNode globNode = entry.isObject() ? entry.get("glob") : null;
        if (globNode == null || !globNode.isTextual()) {
            return false;
        }
        String resourcePath = normalizeResourcePath(globNode.asText());
        return testResources.contains(resourcePath)
                || isTestPackageClassFileGlob(resourcePath, testPackages, allowedPackages);
    }

    /// Whether the `.class` glob `resourcePath` can match only class files in a test package, as
    /// libraries that read class bytes record it (Guice's `$LineNumbers`, `EntityScanner`).
    /// Its literal directories, up to the first segment with a glob metacharacter, must be
    /// identifiers naming a test package or a package under one.
    private boolean isTestPackageClassFileGlob(
            String resourcePath,
            Set<String> testPackages,
            List<String> allowedPackages
    ) {
        if (!resourcePath.endsWith(CLASS_FILE_SUFFIX)) {
            return false;
        }
        String[] segments = resourcePath.split("/", -1);
        List<String> packageSegments = new ArrayList<>();
        for (int i = 0; i < segments.length - 1 && !hasGlobMetacharacter(segments[i]); i++) {
            if (!SourceVersion.isIdentifier(segments[i])) {
                return false;
            }
            packageSegments.add(segments[i]);
        }
        if (packageSegments.isEmpty()) {
            return false;
        }
        String packageName = String.join(".", packageSegments);
        // A test misplaced in a library package (§FS-test-contract.2.2) leaves its classes shipped.
        return referencesAnyPackage(packageName, testPackages)
                && !referencesAnyPackage(packageName, allowedPackages);
    }

    private boolean hasGlobMetacharacter(String segment) {
        return segment.chars().anyMatch(character -> GLOB_METACHARACTERS.indexOf(character) >= 0);
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

    private boolean referencesPackage(String value, String packageName) {
        return value.equals(packageName) || value.startsWith(packageName + ".");
    }

    private String normalizeResourcePath(String path) {
        String normalized = path.replace('\\', '/');
        if (normalized.startsWith("/")) {
            return normalized.substring(1);
        }
        return normalized;
    }

    private ObjectNode retainTestOnlyMetadata(
            ObjectNode existingMetadata,
            Set<String> testPackages,
            Set<String> testResources,
            List<String> allowedPackages
    ) {
        ObjectNode retainedMetadata = objectMapper.createObjectNode();
        splitReflection(existingMetadata.deepCopy(), retainedMetadata, testPackages);
        splitSerialization(existingMetadata.deepCopy(), retainedMetadata, testPackages);
        splitResources(existingMetadata.deepCopy(), retainedMetadata, testPackages, testResources, allowedPackages);
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
        mergeArrayField(merged, additions, "serialization");
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
