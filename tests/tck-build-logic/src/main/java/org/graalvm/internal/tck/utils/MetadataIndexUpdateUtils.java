/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.utils;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.graalvm.internal.tck.Coordinates;
import org.graalvm.internal.tck.model.LibraryLanguage;
import org.graalvm.internal.tck.model.MetadataVersionsIndexEntry;
import org.gradle.api.GradleException;
import org.gradle.api.file.ProjectLayout;
import org.gradle.util.internal.VersionNumber;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Static utility class for updating `metadata/&lt;group&gt;/&lt;artifact&gt;/index.json` entries.
 */
public final class MetadataIndexUpdateUtils {

    private static final String RELEASE_QUALIFIER = "release";
    private static final Pattern METADATA_VERSION_PATTERN = Pattern.compile(
            "^(\\d+(?:\\.\\d+)*)(?:\\.(?:Final|RELEASE))?"
                    + "(?:[-.](alpha\\d*|beta\\d*|rc\\d*|cr\\d*|m\\d+|ea\\d*|b\\d+|\\d+|preview)(?:[-.](.*))?)?$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern QUALIFIER_PATTERN = Pattern.compile(
            "^(alpha|beta|rc|cr|m|ea|b|preview)(\\d*)$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Map<String, Integer> QUALIFIER_RANKS = Map.ofEntries(
            Map.entry("alpha", 10),
            Map.entry("beta", 20),
            Map.entry("m", 30),
            Map.entry("ea", 35),
            Map.entry("preview", 40),
            Map.entry("rc", 50),
            Map.entry("cr", 50),
            Map.entry("b", 60),
            Map.entry("number", 70),
            Map.entry(RELEASE_QUALIFIER, 100)
    );

    private static final ObjectMapper objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private MetadataIndexUpdateUtils() {
    }

    /**
     * Adds packages to the matching index.json entry without removing existing package roots.
     */
    public static void setAllowedPackagesInIndexJson(ProjectLayout layout, Coordinates coordinates, List<String> packages) throws IOException {
        if (packages.isEmpty()) {
            return;
        }

        Path indexFile = GeneralUtils.getPathFromProject(
                layout,
                CoordinateUtils.replace("metadata/$group$/$artifact$/index.json", coordinates)
        );
        if (!Files.isRegularFile(indexFile)) {
            return;
        }

        List<Map<String, Object>> entries = objectMapper.readValue(indexFile.toFile(), new TypeReference<>() {});
        boolean updated = false;
        for (Map<String, Object> entry : entries) {
            if (!matchesGeneratedCoordinate(entry, coordinates)) {
                continue;
            }

            LinkedHashSet<String> allowedPackages = readAllowedPackages(entry);
            int originalSize = allowedPackages.size();
            allowedPackages.addAll(packages);
            if (allowedPackages.size() != originalSize) {
                entry.put("allowed-packages", new ArrayList<>(allowedPackages));
                updated = true;
            }
        }

        if (!updated) {
            return;
        }

        DefaultPrettyPrinter prettyPrinter = new DefaultPrettyPrinter();
        prettyPrinter.indentArraysWith(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE);
        String json = objectMapper.writer(prettyPrinter).writeValueAsString(entries);
        if (!json.endsWith(System.lineSeparator())) {
            json = json + System.lineSeparator();
        }
        Files.writeString(indexFile, json, StandardCharsets.UTF_8);
    }

    public static List<String> mergeWithAllowedPackagesInIndexJson(
            ProjectLayout layout,
            Coordinates coordinates,
            List<String> packages
    ) throws IOException {
        LinkedHashSet<String> mergedPackages = new LinkedHashSet<>();
        Path indexFile = GeneralUtils.getPathFromProject(
                layout,
                CoordinateUtils.replace("metadata/$group$/$artifact$/index.json", coordinates)
        );
        if (Files.isRegularFile(indexFile)) {
            List<Map<String, Object>> entries = objectMapper.readValue(indexFile.toFile(), new TypeReference<>() {});
            for (Map<String, Object> entry : entries) {
                if (matchesGeneratedCoordinate(entry, coordinates)) {
                    mergedPackages.addAll(readAllowedPackages(entry));
                    break;
                }
            }
        }
        mergedPackages.addAll(packages);
        return new ArrayList<>(mergedPackages);
    }

    private static LinkedHashSet<String> readAllowedPackages(Map<String, Object> entry) {
        LinkedHashSet<String> allowedPackages = new LinkedHashSet<>();
        Object currentPackages = entry.get("allowed-packages");
        if (currentPackages instanceof List<?> currentPackageList) {
            for (Object currentPackage : currentPackageList) {
                if (currentPackage instanceof String packageName && !packageName.isBlank()) {
                    allowedPackages.add(packageName);
                }
            }
        }
        return allowedPackages;
    }

    private static boolean matchesGeneratedCoordinate(Map<String, Object> entry, Coordinates coordinates) {
        Object metadataVersion = entry.get("metadata-version");
        if (coordinates.version().equals(metadataVersion)) {
            return true;
        }

        Object testedVersions = entry.get("tested-versions");
        if (testedVersions instanceof List<?> versions) {
            return versions.contains(coordinates.version());
        }
        return false;
    }

    /**
     * Marks the library version identified by {@code newCoords} as the {@code latest} entry
     * within its corresponding {@code index.json}.
     */
    public static void makeVersionLatestInIndexJson(ProjectLayout layout, Coordinates newCoords, String testVersion) throws IOException {
        addVersionToIndexJson(layout, newCoords, testVersion, true);
    }

    /**
     * Adds a metadata entry for {@code newCoords} while preserving the current {@code latest}
     * entry within its corresponding {@code index.json}.
     */
    public static void addVersionToIndexJson(ProjectLayout layout, Coordinates newCoords, String testVersion) throws IOException {
        addVersionToIndexJson(layout, newCoords, testVersion, false);
    }

    /**
     * Adds a metadata entry for {@code newCoords}, promoting it to {@code latest}
     * only when it is parseably newer than the current latest metadata version.
     */
    public static void addVersionToIndexJsonUpdatingLatestWhenNewer(
            ProjectLayout layout,
            Coordinates newCoords,
            String testVersion
    ) throws IOException {
        addVersionToIndexJson(layout, newCoords, testVersion, isNewerThanLatestInIndexJson(layout, newCoords));
    }

    private static boolean isNewerThanLatestInIndexJson(ProjectLayout layout, Coordinates newCoords) throws IOException {
        String indexPathTemplate = "metadata/$group$/$artifact$/index.json";
        File indexFile = GeneralUtils.getPathFromProject(layout, CoordinateUtils.replace(indexPathTemplate, newCoords)).toFile();
        if (!indexFile.exists()) {
            return false;
        }

        ObjectMapper objectMapper = new ObjectMapper();
        List<MetadataVersionsIndexEntry> entries = objectMapper.readValue(indexFile, new TypeReference<>() {});
        String latestVersion = findSingleLatestMetadataVersion(entries);
        if (latestVersion == null) {
            return false;
        }
        return compareParseableMetadataVersions(newCoords.version(), latestVersion) > 0;
    }

    private static String findSingleLatestMetadataVersion(List<MetadataVersionsIndexEntry> entries) {
        String latestVersion = null;
        for (MetadataVersionsIndexEntry entry : entries) {
            if (!Boolean.TRUE.equals(entry.latest())) {
                continue;
            }
            if (latestVersion != null || entry.metadataVersion() == null || entry.metadataVersion().isBlank()) {
                return null;
            }
            latestVersion = entry.metadataVersion();
        }
        return latestVersion;
    }

    private static int compareParseableMetadataVersions(String firstVersion, String secondVersion) {
        ParsedMetadataVersion first = parseMetadataVersion(firstVersion);
        ParsedMetadataVersion second = parseMetadataVersion(secondVersion);
        if (first == null || second == null) {
            return 0;
        }
        return first.compareTo(second);
    }

    private static ParsedMetadataVersion parseMetadataVersion(String version) {
        if (version == null) {
            return null;
        }

        Matcher versionMatcher = METADATA_VERSION_PATTERN.matcher(version);
        if (!versionMatcher.matches()) {
            return null;
        }

        List<Integer> baseComponents = new ArrayList<>();
        for (String component : versionMatcher.group(1).split("\\.")) {
            baseComponents.add(Integer.parseInt(component));
        }

        String qualifierToken = versionMatcher.group(2);
        String qualifierTail = versionMatcher.group(3);
        if (qualifierToken == null) {
            return new ParsedMetadataVersion(baseComponents, QUALIFIER_RANKS.get(RELEASE_QUALIFIER), 0);
        }

        if (qualifierToken.chars().allMatch(Character::isDigit)) {
            if (qualifierTail != null && Stream.of(qualifierTail.split("[-.]"))
                    .anyMatch(part -> part.isBlank() || !part.chars().allMatch(Character::isDigit))) {
                return null;
            }
            return new ParsedMetadataVersion(baseComponents, QUALIFIER_RANKS.get("number"), Integer.parseInt(qualifierToken));
        }

        Matcher qualifierMatcher = QUALIFIER_PATTERN.matcher(qualifierToken);
        if (!qualifierMatcher.matches()) {
            return null;
        }

        String qualifier = qualifierMatcher.group(1).toLowerCase(Locale.ROOT);
        String qualifierNumber = qualifierMatcher.group(2);
        if (qualifierNumber.isBlank() && qualifierTail != null) {
            String firstTailPart = qualifierTail.split("[-.]")[0];
            if (firstTailPart.chars().allMatch(Character::isDigit)) {
                qualifierNumber = firstTailPart;
            }
        }
        return new ParsedMetadataVersion(
                baseComponents,
                QUALIFIER_RANKS.get(qualifier),
                Integer.parseInt(qualifierNumber.isBlank() ? "0" : qualifierNumber)
        );
    }

    private static void addVersionToIndexJson(ProjectLayout layout, Coordinates newCoords, String testVersion, boolean markAsLatest) throws IOException {
        String indexPathTemplate = "metadata/$group$/$artifact$/index.json";
        File indexFile = GeneralUtils.getPathFromProject(layout, CoordinateUtils.replace(indexPathTemplate, newCoords)).toFile();

        ObjectMapper objectMapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);

        // Read existing entries if file exists, otherwise start fresh
        List<MetadataVersionsIndexEntry> entries = new ArrayList<>();
        if (indexFile.exists()) {
            entries = objectMapper.readValue(indexFile, new TypeReference<>() {});
        }
        if (entries.stream().anyMatch(MetadataVersionsIndexEntry::isNotForNativeImage)) {
            throw new GradleException("Cannot mark a new version latest for not-for-native-image artifact: " + newCoords.group() + ":" + newCoords.artifact());
        }

        // If the version already exists, no update needed
        String newVersion = newCoords.version();
        for (MetadataVersionsIndexEntry entry : entries) {
            if (newVersion.equals(entry.metadataVersion())) {
                return;
            }
            List<String> tv = entry.testedVersions();
            if (tv != null && tv.contains(newVersion)) {
                return;
            }
        }

        List<String> latestAllowedPackages = null;
        List<String> latestRequires = null;
        LibraryLanguage latestLanguage = null;
        Boolean latestAutoUpdate = null;
        Boolean latestHighPriority = null;
        // Copy default metadata properties from the current latest entry.
        for (int i = 0; i < entries.size(); i++) {
            MetadataVersionsIndexEntry entry = entries.get(i);
            if (Boolean.TRUE.equals(entry.latest())) {
                latestAllowedPackages = entry.allowedPackages();
                latestRequires = entry.requires();
                latestLanguage = entry.language();
                latestAutoUpdate = entry.autoUpdate();
                latestHighPriority = entry.highPriority();
                if (markAsLatest) {
                    entries.set(i, copyWithLatest(entry, null, null, null));
                }
            }
        }

        // When this creates a new metadata boundary inside an existing range,
        // move covered tested versions to the new entry so index validation keeps passing.
        List<String> testedVersions = moveTestedVersionsCoveredByNewMetadata(entries, newCoords.version());
        MetadataVersionsIndexEntry newEntry = new MetadataVersionsIndexEntry(
                markAsLatest ? Boolean.TRUE : null, // latest
                markAsLatest ? latestAutoUpdate : null, // auto-update
                markAsLatest ? latestHighPriority : null, // high-priority
                null, // override
                null, // default-for
                newCoords.version(), // metadata-version
                testVersion, // test-version
                null, // source-code-url
                null, // repository-url
                null, // test-code-url
                null, // documentation-url
                null, // description
                latestLanguage, // language
                testedVersions,
                null, // skipped-versions
                latestAllowedPackages, // allowed-packages
                latestRequires, // requires
                null, // not-for-native-image
                null, // reason
                null // replacement
        );
        if (markAsLatest) {
            entries.addFirst(newEntry);
        } else {
            entries.add(insertIndexAfterLatest(entries), newEntry);
        }

        DefaultPrettyPrinter prettyPrinter = new DefaultPrettyPrinter();
        prettyPrinter.indentArraysWith(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE);
        String json = objectMapper.writer(prettyPrinter).writeValueAsString(entries);
        if (!json.endsWith(System.lineSeparator())) {
            json = json + System.lineSeparator();
        }
        Files.writeString(indexFile.toPath(), json, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static int insertIndexAfterLatest(List<MetadataVersionsIndexEntry> entries) {
        for (int i = 0; i < entries.size(); i++) {
            if (Boolean.TRUE.equals(entries.get(i).latest())) {
                return i + 1;
            }
        }
        return 0;
    }

    private static List<String> moveTestedVersionsCoveredByNewMetadata(List<MetadataVersionsIndexEntry> entries, String newVersion) {
        VersionNumber newMetadataVersion = parseVersionOrNull(newVersion);
        List<String> movedVersions = new ArrayList<>();
        movedVersions.add(newVersion);

        if (newMetadataVersion == null) {
            return movedVersions;
        }

        VersionNumber nextMetadataVersion = findNextMetadataVersion(entries, newMetadataVersion);
        for (int i = 0; i < entries.size(); i++) {
            MetadataVersionsIndexEntry entry = entries.get(i);
            VersionNumber entryMetadataVersion = parseVersionOrNull(entry.metadataVersion());
            if (entryMetadataVersion == null || entryMetadataVersion.compareTo(newMetadataVersion) >= 0) {
                continue;
            }

            List<String> testedVersions = entry.testedVersions();
            if (testedVersions == null || testedVersions.isEmpty()) {
                continue;
            }

            List<String> retainedVersions = new ArrayList<>();
            boolean moved = false;
            for (String testedVersion : testedVersions) {
                if (isCoveredByNewMetadata(testedVersion, newMetadataVersion, nextMetadataVersion)) {
                    movedVersions.add(testedVersion);
                    moved = true;
                } else {
                    retainedVersions.add(testedVersion);
                }
            }
            if (moved) {
                entries.set(i, copyWithTestedVersions(entry, retainedVersions));
            }
        }

        return new ArrayList<>(new LinkedHashSet<>(movedVersions));
    }

    private static VersionNumber findNextMetadataVersion(List<MetadataVersionsIndexEntry> entries, VersionNumber newMetadataVersion) {
        VersionNumber nextMetadataVersion = null;
        for (MetadataVersionsIndexEntry entry : entries) {
            VersionNumber candidate = parseVersionOrNull(entry.metadataVersion());
            if (candidate == null || candidate.compareTo(newMetadataVersion) <= 0) {
                continue;
            }
            if (nextMetadataVersion == null || candidate.compareTo(nextMetadataVersion) < 0) {
                nextMetadataVersion = candidate;
            }
        }
        return nextMetadataVersion;
    }

    private static boolean isCoveredByNewMetadata(String testedVersion, VersionNumber newMetadataVersion, VersionNumber nextMetadataVersion) {
        VersionNumber parsedTestedVersion = parseVersionOrNull(testedVersion);
        if (parsedTestedVersion == null || parsedTestedVersion.compareTo(newMetadataVersion) < 0) {
            return false;
        }
        return nextMetadataVersion == null || parsedTestedVersion.compareTo(nextMetadataVersion) < 0;
    }

    private static VersionNumber parseVersionOrNull(String version) {
        if (version == null) {
            return null;
        }
        try {
            return VersionNumber.parse(version);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static MetadataVersionsIndexEntry copyWithTestedVersions(MetadataVersionsIndexEntry entry, List<String> testedVersions) {
        return new MetadataVersionsIndexEntry(
                entry.latest(),
                entry.autoUpdate(),
                entry.highPriority(),
                entry.override(),
                entry.defaultFor(),
                entry.metadataVersion(),
                entry.testVersion(),
                entry.sourceCodeUrl(),
                entry.repositoryUrl(),
                entry.testCodeUrl(),
                entry.documentationUrl(),
                entry.description(),
                entry.language(),
                testedVersions,
                entry.skippedVersions(),
                entry.allowedPackages(),
                entry.requires(),
                entry.notForNativeImage(),
                entry.reason(),
                entry.replacement()
        );
    }

    private static MetadataVersionsIndexEntry copyWithLatest(
            MetadataVersionsIndexEntry entry,
            Boolean latest,
            Boolean autoUpdate,
            Boolean highPriority
    ) {
        return new MetadataVersionsIndexEntry(
                latest,
                autoUpdate,
                highPriority,
                entry.override(),
                entry.defaultFor(),
                entry.metadataVersion(),
                entry.testVersion(),
                entry.sourceCodeUrl(),
                entry.repositoryUrl(),
                entry.testCodeUrl(),
                entry.documentationUrl(),
                entry.description(),
                entry.language(),
                entry.testedVersions(),
                entry.skippedVersions(),
                entry.allowedPackages(),
                entry.requires(),
                entry.notForNativeImage(),
                entry.reason(),
                entry.replacement()
        );
    }

    private record ParsedMetadataVersion(List<Integer> baseComponents, int qualifierRank, int qualifierNumber)
            implements Comparable<ParsedMetadataVersion> {
        private ParsedMetadataVersion {
            baseComponents = List.copyOf(baseComponents);
        }

        @Override
        public int compareTo(ParsedMetadataVersion other) {
            int componentCount = Math.max(baseComponents.size(), other.baseComponents.size());
            for (int i = 0; i < componentCount; i++) {
                int component = i < baseComponents.size() ? baseComponents.get(i) : 0;
                int otherComponent = i < other.baseComponents.size() ? other.baseComponents.get(i) : 0;
                int componentComparison = Integer.compare(component, otherComponent);
                if (componentComparison != 0) {
                    return componentComparison;
                }
            }

            int rankComparison = Integer.compare(qualifierRank, other.qualifierRank);
            if (rankComparison != 0) {
                return rankComparison;
            }
            return Integer.compare(qualifierNumber, other.qualifierNumber);
        }
    }
}
