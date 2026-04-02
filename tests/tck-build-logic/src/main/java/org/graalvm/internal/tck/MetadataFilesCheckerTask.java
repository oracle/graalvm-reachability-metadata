/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import groovy.json.JsonSlurper;
import org.graalvm.internal.tck.utils.CoordinateUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Checks content of metadata files for a new library.
 * <p>
 * Run with {@code gradle checkMetadataFiles -Pcoordinates com.example:library:1.0.0}.
 */
@SuppressWarnings("unused")
public abstract class MetadataFilesCheckerTask extends DefaultTask {
    private static final String REACHABILITY_METADATA_FILE_NAME = "reachability-metadata.json";
    private static final String REACHABILITY_METADATA_SCHEMA_PATH = "metadata/schemas/reachability-metadata-schema-v1.2.0.json";
    private static final JsonSchemaFactory REACHABILITY_METADATA_SCHEMA_FACTORY =
            JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V201909);

    @InputFiles
    protected abstract RegularFileProperty getMetadataRoot();

    @InputFiles
    protected abstract RegularFileProperty getIndexFile();

    private final Set<String> EXPECTED_FILES = new HashSet<>(List.of(
            REACHABILITY_METADATA_FILE_NAME));

    private final Set<String> ILLEGAL_TYPE_VALUES = new HashSet<>(List.of("java.lang"));

    private final Set<String> PREDEFINED_ALLOWED_PACKAGES = new HashSet<>(List.of("java.lang", "java.util"));

    Coordinates coordinates;
    private List<String> allowedPackages;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private JsonSchema reachabilityMetadataSchema;

    @Option(option = "coordinates", description = "Coordinates in the form of group:artifact:version")
    public void setCoordinates(String coords) {
        extractCoordinates(coords);
    }

    {
        // Prefer task option, fallback to -Pcoordinates when it looks like a single coordinate (group:artifact:version)
        String prop = (String) getProject().findProperty("coordinates");
        if (prop != null && !getIndexFile().isPresent()) {
            // Skip when using fractional batches (k/n) or 'all'. Only parse exact group:artifact:version.
            if (!CoordinateUtils.isFractionalBatch(prop)) {
                String[] parts = prop.split(":", -1);
                if (parts.length == 3) {
                    extractCoordinates(prop);
                }
            }
        }
    }

    private void extractCoordinates(String c) {
        this.coordinates = Coordinates.parse(c);
        File coordinatesMetadataRoot = resolveMetadataRoot(this.coordinates);
        getMetadataRoot().set(coordinatesMetadataRoot);

        File index = getProject().file(CoordinateUtils.replace("metadata/$group$/$artifact$/index.json", coordinates));
        getIndexFile().set(index);

        this.allowedPackages = getAllowedPackages();
    }

    @SuppressWarnings("unchecked")
    private File resolveMetadataRoot(Coordinates coordinates) {
        File conventionalRoot = getProject().file(CoordinateUtils.replace("metadata/$group$/$artifact$/$version$", coordinates));
        if (conventionalRoot.exists()) {
            return conventionalRoot;
        }

        File artifactRoot = getProject().file("metadata/" + coordinates.group() + "/" + coordinates.artifact());
        File artifactIndex = new File(artifactRoot, "index.json");
        if (!artifactIndex.exists()) {
            return conventionalRoot;
        }

        List<Map<String, Object>> entries = getConfigEntries(artifactIndex);
        for (Map<String, Object> entry : entries) {
            Object testedVersions = entry.get("tested-versions");
            if (testedVersions instanceof List<?> versions && versions.contains(coordinates.version())) {
                Object metadataVersion = entry.get("metadata-version");
                if (metadataVersion instanceof String version) {
                    return new File(artifactRoot, version);
                }
            }
        }

        return conventionalRoot;
    }

    @TaskAction
    public void run() throws IllegalArgumentException {
        File coordinatesMetadataRoot = getMetadataRoot().get().getAsFile();
        if (!coordinatesMetadataRoot.exists()) {
            throw new IllegalArgumentException("ERROR: Cannot find metadata directory for given coordinates: " + this.coordinates);
        }

        boolean containsErrors = false;
        List<File> filesInMetadata = getConfigFilesForMetadataDir(coordinatesMetadataRoot);
        for (File file : filesInMetadata) {
            if (file.getName().equalsIgnoreCase(REACHABILITY_METADATA_FILE_NAME)) {
                containsErrors |= reachabilityMetadataFileContainsErrors(file);
            }
        }

        if (containsErrors) {
            throw new IllegalStateException("Errors above found for: " + this.coordinates);
        }
    }

    private List<File> getConfigFilesForMetadataDir(File root) throws RuntimeException {
        List<File> files = new ArrayList<>();
        File[] content = root.listFiles();

        if (content == null) {
            throw new RuntimeException("ERROR: Failed to load content of " + root.toURI());
        }

        Arrays.stream(content).forEach(file -> {
            if (EXPECTED_FILES.stream().noneMatch(file.getName()::equalsIgnoreCase)) {
                throw new IllegalStateException("ERROR: Unexpected file " + file.toURI() + " found in " + root.toURI());
            }

            files.add(file);
        });

        if (files.stream().noneMatch(file -> file.getName().equalsIgnoreCase(REACHABILITY_METADATA_FILE_NAME))) {
            throw new IllegalStateException("ERROR: Missing " + REACHABILITY_METADATA_FILE_NAME + " in " + root.toURI());
        }

        return files;
    }

    private boolean reachabilityMetadataFileContainsErrors(File file) {
        try {
            JsonNode metadata = objectMapper.readTree(file);
            JsonSchema schema = getReachabilityMetadataSchema();
            boolean containsErrors = false;
            Set<ValidationMessage> errors = schema.validate(metadata);
            if (!errors.isEmpty()) {
                errors.forEach(error -> System.out.println("ERROR: Invalid reachability metadata in " + file.toURI() + ": " + error.getMessage()));
                containsErrors = true;
            }

            containsErrors |= containsDuplicatedEntries(metadata.path("reflection"), "reflection", file);
            containsErrors |= containsDuplicatedEntries(metadata.path("resources"), "resources", file);
            containsErrors |= containsDuplicatedEntries(metadata.path("serialization"), "serialization", file);
            containsErrors |= containsDuplicatedEntries(metadata.path("foreign").path("downcalls"), "foreign.downcalls", file);
            containsErrors |= containsDuplicatedEntries(metadata.path("foreign").path("upcalls"), "foreign.upcalls", file);
            containsErrors |= containsDuplicatedEntries(metadata.path("foreign").path("directUpcalls"), "foreign.directUpcalls", file);
            containsErrors |= containsInvalidTypeReachedEntries(metadata.path("reflection"), file);
            containsErrors |= containsInvalidTypeReachedEntries(metadata.path("resources"), file);
            containsErrors |= containsInvalidTypeReachedEntries(metadata.path("serialization"), file);
            containsErrors |= containsInvalidTypeReachedEntries(metadata.path("foreign").path("downcalls"), file);
            containsErrors |= containsInvalidTypeReachedEntries(metadata.path("foreign").path("upcalls"), file);
            containsErrors |= containsInvalidTypeReachedEntries(metadata.path("foreign").path("directUpcalls"), file);

            return containsErrors;
        } catch (Exception e) {
            System.out.println("ERROR: Failed to parse reachability metadata file " + file.toURI() + ": " + e.getMessage());
            return true;
        }
    }

    private JsonSchema getReachabilityMetadataSchema() throws IOException {
        if (reachabilityMetadataSchema == null) {
            File schemaFile = getProject().file(REACHABILITY_METADATA_SCHEMA_PATH);
            JsonNode schemaRoot = objectMapper.readTree(schemaFile);
            if (!(schemaRoot instanceof ObjectNode schemaObject)) {
                throw new IllegalStateException("ERROR: Invalid reachability metadata schema in " + schemaFile.toURI());
            }

            // The checked-in schema includes a non-standard "version" keyword and does not
            // yet model the repository's legacy top-level "serialization" block.
            ObjectNode adjustedSchema = schemaObject.deepCopy();
            adjustedSchema.remove("version");
            ObjectNode properties = requireObjectNode(adjustedSchema, "properties", schemaFile);
            if (!properties.has("serialization")) {
                properties.set("serialization", createLegacySerializationSchema());
            }

            reachabilityMetadataSchema = REACHABILITY_METADATA_SCHEMA_FACTORY.getSchema(adjustedSchema);
        }

        return reachabilityMetadataSchema;
    }

    private ObjectNode requireObjectNode(ObjectNode parent, String fieldName, File schemaFile) {
        JsonNode node = parent.get(fieldName);
        if (!(node instanceof ObjectNode objectNode)) {
            throw new IllegalStateException("ERROR: Schema file " + schemaFile.toURI()
                    + " is missing an object-valued '" + fieldName + "' node");
        }
        return objectNode;
    }

    private ObjectNode createLegacySerializationSchema() {
        ObjectNode serializationSchema = objectMapper.createObjectNode();
        serializationSchema.put("title", "Legacy serialization metadata supported by this repository");
        serializationSchema.put("type", "array");
        serializationSchema.putArray("default");

        ObjectNode itemSchema = serializationSchema.putObject("items");
        itemSchema.put("title", "Type that should be registered for serialization");
        itemSchema.put("type", "object");

        ObjectNode properties = itemSchema.putObject("properties");
        properties.putObject("reason").put("$ref", "#/$defs/reason");
        properties.putObject("condition").put("$ref", "#/$defs/condition");
        properties.putObject("type").put("$ref", "#/$defs/className");

        itemSchema.putArray("required").add("type");
        itemSchema.put("additionalProperties", false);
        return serializationSchema;
    }

    @SuppressWarnings("unchecked")
    // in case when config file is an array of entries
    private List<Map<String, Object>> getConfigEntries(File file) {
        return ((List<Object>) new JsonSlurper()
                .parse(file))
                .stream()
                .map(e -> (Map<String, Object>) e)
                .collect(Collectors.toList());
    }

    private boolean containsDuplicatedEntries(JsonNode entries, String sectionName, File file) {
        if (!entries.isArray() || entries.isEmpty()) {
            return false;
        }

        Map<JsonNode, Integer> duplicates = new LinkedHashMap<>();
        entries.forEach(entry -> duplicates.merge(entry, 1, Integer::sum));

        boolean containsDuplicates = false;
        for (Map.Entry<JsonNode, Integer> entry : duplicates.entrySet()) {
            if (entry.getValue() > 1) {
                containsDuplicates = true;
                System.out.println("ERROR: In file " + file.toURI() + " there is a duplicated " + sectionName +
                        " entry " + describeEntry(entry.getKey()));
            }
        }

        return containsDuplicates;
    }

    private boolean containsInvalidTypeReachedEntries(JsonNode entries, File file) {
        if (!entries.isArray() || entries.isEmpty()) {
            return false;
        }

        boolean containsErrors = false;
        for (JsonNode entry : entries) {
            containsErrors |= checkTypeReached(entry, file);
            containsErrors |= containsEntriesNotFromLibrary(entry, file);
        }
        return containsErrors;
    }

    private boolean checkTypeReached(JsonNode entry, File file) {
        String typeReached = getEntryTypeReached(entry);
        if (typeReached == null) {
            return false;
        }

        String entryDescription = describeEntry(entry);
        if (isAllowedPredefinedEntry(typeReached, entry)) {
            return false;
        }

        if (ILLEGAL_TYPE_VALUES.stream().anyMatch(typeReached::startsWith)) {
            System.out.println("ERROR: In file " + file.toURI() + " entry: " + entryDescription + " contains illegal typeReached value. Field" +
                    " typeReached cannot be any of the following values: " + ILLEGAL_TYPE_VALUES);
            return true;
        }

        return false;
    }

    private boolean containsEntriesNotFromLibrary(JsonNode entry, File file) {
        String typeReached = getEntryTypeReached(entry);
        if (typeReached == null) {
            return false;
        }

        String entryDescription = describeEntry(entry);
        if (isAllowedPredefinedEntry(typeReached, entry)) {
            return false;
        }

        if (this.allowedPackages.stream().noneMatch(typeReached::contains)) {
            System.out.println("ERROR: In file " + file.toURI() + "\n" +
                    "Entry: " + entryDescription + "\n" +
                    "TypeReached: " + typeReached + "\n" +
                    "doesn't belong to any of the specified packages: " + this.allowedPackages + "\n");
            return true;
        }

        return false;
    }

    private boolean isAllowedPredefinedEntry(String typeReached, JsonNode entry) {
        String entryName = getEntryName(entry);
        return entryName != null
                && PREDEFINED_ALLOWED_PACKAGES.stream().anyMatch(typeReached::contains)
                && PREDEFINED_ALLOWED_PACKAGES.stream().anyMatch(entryName::contains);
    }

    private String getEntryTypeReached(JsonNode entry) {
        JsonNode condition = entry.path("condition");
        if (!condition.isObject() || !condition.hasNonNull("typeReached")) {
            return null;
        }
        return condition.get("typeReached").asText();
    }

    private String getEntryName(JsonNode entry) {
        if (entry.hasNonNull("name")) {
            return entry.get("name").asText();
        }
        if (entry.has("type") && entry.get("type").isTextual()) {
            return entry.get("type").asText();
        }
        return null;
    }

    private String describeEntry(JsonNode entry) {
        if (entry == null || entry.isMissingNode() || entry.isNull()) {
            return "<missing>";
        }
        if (entry.hasNonNull("name")) {
            return entry.get("name").asText();
        }
        if (entry.hasNonNull("glob")) {
            return entry.get("glob").asText();
        }
        if (entry.hasNonNull("bundle")) {
            return entry.get("bundle").asText();
        }
        if (entry.hasNonNull("class")) {
            return entry.get("class").asText();
        }
        if (entry.hasNonNull("method")) {
            return entry.get("method").asText();
        }
        if (entry.has("type")) {
            JsonNode type = entry.get("type");
            if (type.isTextual()) {
                return type.asText();
            }
            if (type.has("proxy")) {
                return type.get("proxy").toString();
            }
            if (type.has("lambda")) {
                return type.get("lambda").toString();
            }
        }
        return entry.toString();
    }

    @SuppressWarnings("unchecked")
    private List<String> getAllowedPackages() {
        File indexFile = getIndexFile().get().getAsFile();
        String groupId = coordinates.group();
        String artifactId = coordinates.artifact();
        String requestedVersion = coordinates.version();

        if (!indexFile.exists()) {
            throw new IllegalStateException("Missing artifact-level index.json: " + indexFile.toURI() + " for coordinates " + coordinates);
        }

        Object parsed = new JsonSlurper().parse(indexFile);
        if (!(parsed instanceof List)) {
            throw new IllegalStateException("Invalid artifact-level index.json (expected array): " + indexFile.toURI());
        }

        List<Map<String, Object>> entries = ((List<Object>) parsed).stream()
                .map(entry -> (Map<String, Object>) entry)
                .toList();

        Optional<Map<String, Object>> byMetadataVersion = entries.stream()
                .filter(entry -> Objects.equals(requestedVersion, entry.get("metadata-version")))
                .findFirst();

        Map<String, Object> match = byMetadataVersion.orElseGet(() ->
                entries.stream()
                        .filter(entry -> {
                            Object testedVersions = entry.get("tested-versions");
                            return testedVersions instanceof List<?> versions && versions.contains(requestedVersion);
                        })
                        .findFirst()
                        .orElse(null)
        );

        if (match == null) {
            List<String> metadataVersions = entries.stream()
                    .map(entry -> (String) entry.get("metadata-version"))
                    .filter(Objects::nonNull)
                    .toList();
            List<String> testedVersions = entries.stream()
                    .map(entry -> {
                        Object value = entry.get("tested-versions");
                        if (value instanceof List<?> versions) {
                            return versions.stream().map(Object::toString).collect(Collectors.toList());
                        }
                        return Collections.<String>emptyList();
                    })
                    .flatMap(List::stream)
                    .distinct()
                    .sorted()
                    .toList();

            throw new IllegalStateException(
                    "Missing index entry for " + groupId + ":" + artifactId +
                    " matching version=" + requestedVersion +
                    " in " + indexFile.toURI() +
                    ". Known metadata-versions=" + metadataVersions +
                    ", tested-versions=" + testedVersions);
        }

        Object allowedPackagesValue = match.get("allowed-packages");
        if (allowedPackagesValue instanceof List) {
            return (List<String>) allowedPackagesValue;
        }
        throw new IllegalStateException(
                "Missing or invalid allowed-packages for " + groupId + ":" + artifactId +
                " (metadata-version=" + match.get("metadata-version") + ") in " + indexFile.toURI());
    }

}
