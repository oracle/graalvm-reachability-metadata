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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.graalvm.internal.tck.Coordinates;
import org.graalvm.internal.tck.utils.MetadataEntryOwnership;
import org.gradle.api.GradleException;
import org.gradle.api.logging.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Relocates foreign-condition metadata to an exact supported owner/version.
 *
 * Owner discovery uses checked-in {@code allowed-packages} and
 * {@code tested-versions}; it never guesses support or deletes unresolved
 * evidence. Shared buckets are forked before the new entry is added.
 * §FS-metadata
 */
final class MetadataOwnershipRouter {
    private static final String INDEX_FILE = "index.json";
    private static final String METADATA_FILE = "reachability-metadata.json";
    private static final List<String> LATEST_ONLY_FIELDS = List.of("latest", "auto-update", "high-priority");
    private static final List<SectionPath> SECTION_PATHS = List.of(
            new SectionPath("reflection", null),
            new SectionPath("resources", null),
            new SectionPath("serialization", null),
            new SectionPath("foreign", "downcalls"),
            new SectionPath("foreign", "upcalls"),
            new SectionPath("foreign", "directUpcalls")
    );

    private final ObjectMapper objectMapper;
    private final Logger logger;

    MetadataOwnershipRouter(ObjectMapper objectMapper, Logger logger) {
        this.objectMapper = objectMapper;
        this.logger = logger;
    }

    Set<String> route(Path metadataRoot, Coordinates sourceCoordinates, ObjectNode sourceMetadata) throws IOException {
        SourceSupport sourceSupport = resolveSourceSupport(metadataRoot, sourceCoordinates);
        List<OwnerCandidate> ownerCatalog = loadOwnerCatalog(metadataRoot, sourceCoordinates.version());
        Map<OwnerKey, OwnerPlan> plans = new LinkedHashMap<>();
        List<ForeignEntry> foreignEntries = new ArrayList<>();

        for (SectionPath sectionPath : SECTION_PATHS) {
            ArrayNode entries = sectionPath.entries(sourceMetadata);
            if (entries == null) {
                continue;
            }
            for (JsonNode entry : entries) {
                if (!MetadataEntryOwnership.isForeign(entry, sourceSupport.allowedPackages())) {
                    continue;
                }
                String typeReached = Objects.requireNonNull(MetadataEntryOwnership.typeReached(entry));
                OwnerCandidate owner = resolveOwner(ownerCatalog, sourceCoordinates, typeReached, entry);
                OwnerKey key = new OwnerKey(owner.artifactDirectory(), owner.entryIndex());
                OwnerPlan plan = plans.computeIfAbsent(key, ignored -> new OwnerPlan(owner));
                plan.add(sectionPath, entry);
                foreignEntries.add(new ForeignEntry(sectionPath, entry));
            }
        }

        if (foreignEntries.isEmpty()) {
            return Set.of();
        }

        Set<String> touchedCoordinates = new LinkedHashSet<>();
        for (OwnerPlan plan : plans.values()) {
            Destination destination = prepareDestination(plan.owner(), sourceCoordinates.version());
            mergeEntries(destination.metadata(), plan.entriesBySection());
            writeJson(destination.metadataFile(), destination.metadata());
            if (destination.indexChanged()) {
                writeJson(destination.indexFile(), destination.index());
            }
            String ownerCoordinate = plan.owner().group() + ":" + plan.owner().artifact() + ":" + sourceCoordinates.version();
            touchedCoordinates.add(ownerCoordinate);
            logger.lifecycle(
                    "Relocated {} foreign metadata entr{} from {} to {}",
                    plan.entryCount(),
                    plan.entryCount() == 1 ? "y" : "ies",
                    sourceCoordinates,
                    ownerCoordinate
            );
        }

        removeEntries(sourceMetadata, foreignEntries);
        return touchedCoordinates;
    }

    private SourceSupport resolveSourceSupport(Path metadataRoot, Coordinates coordinates) throws IOException {
        Path indexFile = metadataRoot.resolve(coordinates.group()).resolve(coordinates.artifact()).resolve(INDEX_FILE);
        if (!Files.isRegularFile(indexFile)) {
            throw new GradleException("Cannot resolve metadata ownership without " + indexFile);
        }
        ArrayNode index = requireArray(objectMapper.readTree(indexFile.toFile()), indexFile);
        for (JsonNode rawEntry : index) {
            if (!(rawEntry instanceof ObjectNode entry) || !supportsVersion(entry, coordinates.version())) {
                continue;
            }
            return new SourceSupport(readStringArray(entry.get("allowed-packages"), "allowed-packages", indexFile));
        }
        throw new GradleException("No index entry in " + indexFile + " supports " + coordinates.version());
    }

    private List<OwnerCandidate> loadOwnerCatalog(Path metadataRoot, String testedVersion) throws IOException {
        List<OwnerCandidate> candidates = new ArrayList<>();
        if (!Files.isDirectory(metadataRoot)) {
            return candidates;
        }
        try (Stream<Path> paths = Files.walk(metadataRoot, 3)) {
            for (Path indexFile : paths
                    .filter(path -> INDEX_FILE.equals(path.getFileName().toString()))
                    .sorted()
                    .toList()) {
                Path artifactDirectory = indexFile.getParent();
                Path relative = metadataRoot.relativize(artifactDirectory);
                if (relative.getNameCount() != 2) {
                    continue;
                }
                ArrayNode index = requireArray(objectMapper.readTree(indexFile.toFile()), indexFile);
                for (int entryIndex = 0; entryIndex < index.size(); entryIndex++) {
                    JsonNode rawEntry = index.get(entryIndex);
                    if (!(rawEntry instanceof ObjectNode entry) || !supportsVersion(entry, testedVersion)) {
                        continue;
                    }
                    List<String> allowedPackages = readStringArray(entry.get("allowed-packages"), "allowed-packages", indexFile);
                    String metadataVersion = requiredText(entry, "metadata-version", indexFile);
                    candidates.add(new OwnerCandidate(
                            relative.getName(0).toString(),
                            relative.getName(1).toString(),
                            artifactDirectory,
                            indexFile,
                            index,
                            entryIndex,
                            entry,
                            metadataVersion,
                            allowedPackages
                    ));
                }
            }
        }
        return candidates;
    }

    private OwnerCandidate resolveOwner(
            List<OwnerCandidate> ownerCatalog,
            Coordinates sourceCoordinates,
            String typeReached,
            JsonNode entry
    ) {
        List<OwnerCandidate> matches = ownerCatalog.stream()
                .filter(candidate -> !candidate.group().equals(sourceCoordinates.group())
                        || !candidate.artifact().equals(sourceCoordinates.artifact()))
                .filter(candidate -> MetadataEntryOwnership.matchesAllowedPackage(typeReached, candidate.allowedPackages()))
                .toList();
        if (matches.size() == 1) {
            return matches.getFirst();
        }

        String entryDescription = MetadataEntryOwnership.describeEntry(entry);
        if (matches.isEmpty()) {
            throw new GradleException(
                    "Cannot relocate metadata entry " + entryDescription + ": no supported artifact maps "
                            + typeReached + " for tested version " + sourceCoordinates.version()
            );
        }
        String owners = matches.stream()
                .map(candidate -> candidate.group() + ":" + candidate.artifact())
                .distinct()
                .sorted()
                .reduce((left, right) -> left + ", " + right)
                .orElse("<none>");
        throw new GradleException(
                "Cannot relocate metadata entry " + entryDescription + ": " + typeReached
                        + " has multiple supported owners for tested version " + sourceCoordinates.version() + ": " + owners
        );
    }

    private Destination prepareDestination(OwnerCandidate owner, String testedVersion) throws IOException {
        List<String> testedVersions = readStringArray(owner.entry().get("tested-versions"), "tested-versions", owner.indexFile());
        Path inheritedMetadataFile = owner.artifactDirectory().resolve(owner.metadataVersion()).resolve(METADATA_FILE);
        ObjectNode inheritedMetadata = readMetadata(inheritedMetadataFile);
        if (testedVersions.size() == 1) {
            return new Destination(
                    inheritedMetadataFile,
                    inheritedMetadata,
                    owner.indexFile(),
                    owner.index(),
                    false
            );
        }
        if (owner.metadataVersion().equals(testedVersion)) {
            throw new GradleException(
                    "Cannot isolate " + testedVersion + " in shared metadata bucket " + owner.metadataVersion()
                            + " for " + owner.group() + ":" + owner.artifact()
            );
        }

        Path exactMetadataFile = owner.artifactDirectory().resolve(testedVersion).resolve(METADATA_FILE);
        if (Files.exists(exactMetadataFile) || hasMetadataVersion(owner.index(), testedVersion)) {
            throw new GradleException(
                    "Cannot fork metadata for " + owner.group() + ":" + owner.artifact() + ":" + testedVersion
                            + ": exact-version destination already exists"
            );
        }

        ObjectNode exactEntry = owner.entry().deepCopy();
        exactEntry.put("metadata-version", testedVersion);
        exactEntry.putArray("tested-versions").add(testedVersion);
        if (!exactEntry.hasNonNull("test-version")) {
            exactEntry.put("test-version", owner.metadataVersion());
        }
        if (owner.entry().path("latest").asBoolean(false)) {
            owner.entry().remove(LATEST_ONLY_FIELDS);
        } else {
            exactEntry.remove(LATEST_ONLY_FIELDS);
        }

        ArrayNode retainedVersions = objectMapper.createArrayNode();
        testedVersions.stream()
                .filter(version -> !version.equals(testedVersion))
                .forEach(retainedVersions::add);
        owner.entry().set("tested-versions", retainedVersions);
        owner.index().insert(owner.entryIndex() + 1, exactEntry);

        return new Destination(
                exactMetadataFile,
                inheritedMetadata.deepCopy(),
                owner.indexFile(),
                owner.index(),
                true
        );
    }

    private void mergeEntries(ObjectNode metadata, Map<SectionPath, List<JsonNode>> entriesBySection) {
        entriesBySection.forEach((sectionPath, additions) -> {
            ArrayNode target = sectionPath.ensureEntries(metadata, objectMapper);
            for (JsonNode addition : additions) {
                if (!contains(target, addition)) {
                    target.add(addition.deepCopy());
                }
            }
        });
    }

    private void removeEntries(ObjectNode metadata, List<ForeignEntry> entries) {
        Map<SectionPath, List<JsonNode>> entriesBySection = new LinkedHashMap<>();
        entries.forEach(entry -> entriesBySection
                .computeIfAbsent(entry.sectionPath(), ignored -> new ArrayList<>())
                .add(entry.entry()));
        entriesBySection.forEach((sectionPath, removals) -> {
            ArrayNode source = sectionPath.entries(metadata);
            if (source == null) {
                return;
            }
            ArrayNode retained = objectMapper.createArrayNode();
            for (JsonNode entry : source) {
                if (!removals.contains(entry)) {
                    retained.add(entry);
                }
            }
            sectionPath.replaceEntries(metadata, retained);
        });
    }

    private ObjectNode readMetadata(Path metadataFile) throws IOException {
        if (!Files.isRegularFile(metadataFile)) {
            throw new GradleException("Cannot relocate metadata into missing file " + metadataFile);
        }
        JsonNode metadata = objectMapper.readTree(metadataFile.toFile());
        if (metadata instanceof ObjectNode objectNode) {
            return objectNode;
        }
        throw new GradleException("Expected object JSON in " + metadataFile);
    }

    private boolean supportsVersion(ObjectNode entry, String testedVersion) {
        JsonNode versions = entry.get("tested-versions");
        if (!(versions instanceof ArrayNode array)) {
            return false;
        }
        for (JsonNode version : array) {
            if (version.isTextual() && version.asText().equals(testedVersion)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasMetadataVersion(ArrayNode index, String metadataVersion) {
        for (JsonNode entry : index) {
            if (entry.path("metadata-version").asText().equals(metadataVersion)) {
                return true;
            }
        }
        return false;
    }

    private List<String> readStringArray(JsonNode node, String fieldName, Path file) {
        if (!(node instanceof ArrayNode array)) {
            throw new GradleException("Missing or invalid " + fieldName + " in " + file);
        }
        List<String> values = new ArrayList<>();
        for (JsonNode value : array) {
            if (!value.isTextual()) {
                throw new GradleException("Non-string " + fieldName + " entry in " + file);
            }
            values.add(value.asText());
        }
        return values;
    }

    private String requiredText(ObjectNode node, String fieldName, Path file) {
        JsonNode value = node.get(fieldName);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new GradleException("Missing or invalid " + fieldName + " in " + file);
        }
        return value.asText();
    }

    private ArrayNode requireArray(JsonNode node, Path path) {
        if (node instanceof ArrayNode arrayNode) {
            return arrayNode;
        }
        throw new GradleException("Expected array JSON in " + path);
    }

    private boolean contains(ArrayNode array, JsonNode expected) {
        for (JsonNode entry : array) {
            if (entry.equals(expected)) {
                return true;
            }
        }
        return false;
    }

    void writeJson(Path file, JsonNode content) throws IOException {
        DefaultIndenter indenter = new DefaultIndenter("  ", "\n");
        DefaultPrettyPrinter prettyPrinter = new DefaultPrettyPrinter();
        prettyPrinter.indentObjectsWith(indenter);
        prettyPrinter.indentArraysWith(indenter);
        String json = objectMapper.writer(prettyPrinter).writeValueAsString(content);
        if (!json.endsWith("\n")) {
            json += "\n";
        }
        Files.createDirectories(file.getParent());
        Files.writeString(file, json, StandardCharsets.UTF_8);
    }

    private record SourceSupport(List<String> allowedPackages) {
    }

    private record OwnerKey(Path artifactDirectory, int entryIndex) {
    }

    private record OwnerCandidate(
            String group,
            String artifact,
            Path artifactDirectory,
            Path indexFile,
            ArrayNode index,
            int entryIndex,
            ObjectNode entry,
            String metadataVersion,
            List<String> allowedPackages
    ) {
    }

    private static final class OwnerPlan {
        private final OwnerCandidate owner;
        private final Map<SectionPath, List<JsonNode>> entriesBySection = new LinkedHashMap<>();

        private OwnerPlan(OwnerCandidate owner) {
            this.owner = owner;
        }

        private void add(SectionPath sectionPath, JsonNode entry) {
            entriesBySection.computeIfAbsent(sectionPath, ignored -> new ArrayList<>()).add(entry);
        }

        private OwnerCandidate owner() {
            return owner;
        }

        private Map<SectionPath, List<JsonNode>> entriesBySection() {
            return entriesBySection;
        }

        private int entryCount() {
            return entriesBySection.values().stream().mapToInt(List::size).sum();
        }
    }

    private record ForeignEntry(SectionPath sectionPath, JsonNode entry) {
    }

    private record Destination(
            Path metadataFile,
            ObjectNode metadata,
            Path indexFile,
            ArrayNode index,
            boolean indexChanged
    ) {
    }

    private record SectionPath(String topLevel, String nested) {
        private ArrayNode entries(ObjectNode metadata) {
            JsonNode parent = nested == null ? metadata : metadata.get(topLevel);
            JsonNode entries = nested == null
                    ? metadata.get(topLevel)
                    : parent == null ? null : parent.get(nested);
            return entries instanceof ArrayNode arrayNode ? arrayNode : null;
        }

        private ArrayNode ensureEntries(ObjectNode metadata, ObjectMapper objectMapper) {
            if (nested == null) {
                JsonNode existing = metadata.get(topLevel);
                if (existing instanceof ArrayNode arrayNode) {
                    return arrayNode;
                }
                ArrayNode created = objectMapper.createArrayNode();
                metadata.set(topLevel, created);
                return created;
            }
            ObjectNode parent = metadata.get(topLevel) instanceof ObjectNode objectNode
                    ? objectNode
                    : metadata.putObject(topLevel);
            JsonNode existing = parent.get(nested);
            if (existing instanceof ArrayNode arrayNode) {
                return arrayNode;
            }
            ArrayNode created = objectMapper.createArrayNode();
            parent.set(nested, created);
            return created;
        }

        private void replaceEntries(ObjectNode metadata, ArrayNode retained) {
            if (nested == null) {
                if (retained.isEmpty()) {
                    metadata.remove(topLevel);
                } else {
                    metadata.set(topLevel, retained);
                }
                return;
            }
            JsonNode parentNode = metadata.get(topLevel);
            if (!(parentNode instanceof ObjectNode parent)) {
                return;
            }
            if (retained.isEmpty()) {
                parent.remove(nested);
                if (parent.isEmpty()) {
                    metadata.remove(topLevel);
                }
            } else {
                parent.set(nested, retained);
            }
        }
    }
}
