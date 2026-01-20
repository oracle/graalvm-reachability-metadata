/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.graalvm.internal.tck.model.MetadataVersionsIndexEntry;

import org.graalvm.internal.tck.utils.CoordinateUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFiles;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.gradle.util.internal.VersionNumber;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("unused")
public abstract class TestedVersionUpdaterTask extends DefaultTask {
    /**
     * Identifies library versions, including optional pre-release, ".Final" and ".RELEASE" suffixes.
     * <p>
     * A version is considered a pre-release if it has a suffix (following the last '.' or '-') matching
     * one of these case-insensitive patterns:
     * <ul>
     *   <li>{@code alpha} followed by optional numbers (e.g., "alpha", "Alpha1", "alpha123")</li>
     *   <li>{@code beta} followed by optional numbers (e.g., "beta", "Beta2", "BETA45")</li>
     *   <li>{@code rc} followed by optional numbers (e.g., "rc", "RC1", "rc99")</li>
     *   <li>{@code cr} followed by optional numbers (e.g., "cr", "CR3", "cr10")</li>
     *   <li>{@code m} followed by REQUIRED numbers (e.g., "M1", "m23")</li>
     *   <li>{@code ea} followed by optional numbers (e.g., "ea", "ea2", "ea15")</li>
     *   <li>{@code b} followed by REQUIRED numbers (e.g., "b0244", "b5")</li>
     *   <li>{@code preview} followed by optional numbers (e.g., "preview", "preview1", "preview42")</li>
     *   <li>Numeric suffixes separated by '-' (e.g., "-1", "-123")</li>
     * </ul>
     * <p>
     * Versions ending with ".Final" or `.RELEASE` are treated as full releases of the base version.
     */
    public static final Pattern VERSION_PATTERN = Pattern.compile(
            "(?i)^(\\d+(?:\\.\\d+)*)(?:\\.Final|\\.RELEASE)?(?:[-.](alpha\\d*|beta\\d*|rc\\d*|cr\\d*|m\\d+|ea\\d*|b\\d+|\\d+|preview)(?:[-.].*)?)?$"
    );

    @Option(option = "coordinates", description = "GAV coordinates of the library")
    void setCoordinates(String c) {
        extractInformationFromCoordinates(c);
    }

    {
        // Prefer task option, fallback to -Pcoordinates
        String coordinates = (String) getProject().findProperty("coordinates");
        if (coordinates != null && !getIndexFile().isPresent()) {
            extractInformationFromCoordinates(coordinates);
        }
    }

    private void extractInformationFromCoordinates(String c) {
        String[] coordinatesParts = c.split(":");
        if (coordinatesParts.length != 3) {
            throw new IllegalArgumentException("Maven coordinates should have 3 parts");
        }
        String group = coordinatesParts[0];
        String artifact = coordinatesParts[1];
        String version = coordinatesParts[2];
        Coordinates coordinates = new Coordinates(group, artifact, version);

        getIndexFile().set(getProject().file(CoordinateUtils.replace("metadata/$group$/$artifact$/index.json", coordinates)));
        getNewVersion().set(version);
    }

    @Input
    @Option(option = "lastSupportedVersion", description = "Last version of the library that passed tests")
    protected abstract Property<@NotNull String> getLastSupportedVersion();

    @Input
    protected abstract Property<@NotNull String> getNewVersion();

    @OutputFiles
    protected abstract RegularFileProperty getIndexFile();

    @TaskAction
    void run() throws IllegalStateException, IOException {
        File coordinatesMetadataIndex = getIndexFile().get().getAsFile();
        String newVersion = getNewVersion().get();
        ObjectMapper objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).setSerializationInclusion(JsonInclude.Include.NON_NULL);

        List<MetadataVersionsIndexEntry> entries = objectMapper.readValue(coordinatesMetadataIndex, new TypeReference<>() {});
        for (int i = 0; i < entries.size(); i++) {
            MetadataVersionsIndexEntry entry = entries.get(i);

            if (entry.testedVersions().contains(getLastSupportedVersion().get())) {
                entry.testedVersions().add(newVersion);
                entry.testedVersions().sort(Comparator.comparing(VersionNumber::parse));

                entries.set(i, handlePreReleases(entry, newVersion, coordinatesMetadataIndex.toPath().getParent(), entries));
            }
        }

        DefaultPrettyPrinter prettyPrinter = new DefaultPrettyPrinter();
        prettyPrinter.indentArraysWith(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE);

        // Ensure the JSON file ends with a trailing EOL
        String json = objectMapper.writer(prettyPrinter).writeValueAsString(entries);
        if (!json.endsWith("\n")) {
            json = json + System.lineSeparator();
        }
        Files.writeString(coordinatesMetadataIndex.toPath(), json, java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * Handles pre-release versions when adding a new version to a metadata entry.
     * <p>
     * Rules applied by this method:
     * <ul>
     *   <li>If the newly added version is a full release (no pre-release label, or ending with ".Final" or ".RELEASE"),
     *       all existing pre-releases of the same base version are removed from {@code testedVersions}.</li>
     *   <li>If the newly added version is itself a pre-release, no versions are removed.</li>
     *   <li>If the entry's {@code metadataVersion} is a pre-release of the same base version,
     *       it is updated to the new full release. The corresponding metadata and test directories are renamed accordingly.
     *       The {@code gradle.properties} file in the tests directory is updated to refer to the new version.</li>
     *   <li>Version parsing follows {@link #VERSION_PATTERN} and treats ".Final" and ".RELEASE" as a base version.</li>
     * </ul>
     */
    private MetadataVersionsIndexEntry handlePreReleases(MetadataVersionsIndexEntry entry, String newVersion, Path baseDir, List<MetadataVersionsIndexEntry> entries) throws IOException {
        Matcher versionMatcher = VERSION_PATTERN.matcher(newVersion);
        if (!versionMatcher.matches()) return entry; // skip invalid formats

        String baseVersion = versionMatcher.group(1);
        // Only remove old pre-releases if this is a full release
        if (versionMatcher.group(2) == null) {
            entry.testedVersions().removeIf(version -> {
                Matcher existingVersionMatcher = VERSION_PATTERN.matcher(version);
                return existingVersionMatcher.matches() && existingVersionMatcher.group(2) != null && baseVersion.equals(existingVersionMatcher.group(1));
            });

            // Update metadata version if it was a pre-release of the same base
            String oldMetadata = entry.metadataVersion();
            Matcher metaMatcher = VERSION_PATTERN.matcher(oldMetadata);
            if (metaMatcher.matches() && metaMatcher.group(2) != null && baseVersion.equals(metaMatcher.group(1))) {
                Path oldDir = baseDir.resolve(oldMetadata);
                Path newDir = baseDir.resolve(newVersion);
                if (Files.exists(oldDir)) Files.move(oldDir, newDir);

                updateTests(baseDir, entry, oldMetadata, newVersion);
                updateDependentTestVersions(oldMetadata, newVersion, entries);
                return new MetadataVersionsIndexEntry(
                        entry.latest(),
                        entry.override(),
                        entry.defaultFor(),
                        newVersion,
                        entry.testVersion(),
                        entry.testedVersions(),
                        entry.skippedVersions(),
                        entry.allowedPackages(),
                        entry.requires()
                );
            }
        }

        return entry;
    }

    /**
     * Updates the tests directory when a pre-release version is replaced by its corresponding
     * full release.
     * <p>
     * This method performs two operations:
     * <ol>
     *   <li>Renames the test directory from {@code oldVersion} to {@code newVersion} under {@code tests/src/<group>/<artifact>/}.</li>
     *   <li>Updates the {@code gradle.properties} file inside the renamed directory:
     *     <ul>
     *       <li>{@code library.version} is set to the new version,</li>
     *       <li>{@code metadata.dir} is updated to point to the metadata directory for the new version.</li>
     *     </ul>
     *   </li>
     * </ol>
     */
    private void updateTests(Path metadataBaseDir, MetadataVersionsIndexEntry entry, String oldVersion, String newVersion) throws IOException {
        // metadataBaseDir points to metadata/<group>/<artifact>
        String artifact = metadataBaseDir.getFileName().toString();
        String group = metadataBaseDir.getParent().getFileName().toString();
        Path testsRoot = metadataBaseDir.getParent().getParent().getParent()
                .resolve("tests/src").resolve(group).resolve(artifact);

        Path oldTestDir = testsRoot.resolve(oldVersion);
        Path newTestDir = testsRoot.resolve(newVersion);

        if (Files.exists(oldTestDir)) {
            Files.move(oldTestDir, newTestDir);
            updateGradleProperties(newTestDir, group, artifact, entry, newVersion);
        }
    }

    /**
     * Updates {@code gradle.properties} inside a given test directory to reflect the new version.
     */
    private void updateGradleProperties(Path testDir, String group, String artifact, MetadataVersionsIndexEntry entry, String newVersion) throws IOException {
        Path gradleProps = testDir.resolve("gradle.properties");
        if (!Files.exists(gradleProps)) return;

        List<String> lines = Files.readAllLines(gradleProps);
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith("library.version")) lines.set(i, "library.version = " + newVersion);
            else if (lines.get(i).startsWith("library.coordinates")) lines.set(i, "library.coordinates = " + group + ":" + artifact + ":" + newVersion);
            else if (lines.get(i).startsWith("metadata.dir"))
                lines.set(i, "metadata.dir = " + group + "/" + artifact + "/" + newVersion + "/");
        }
        Files.write(gradleProps, lines);
    }

    /**
     * Checks all entries in the index file to see if any are using the old metadata version
     * (which was a pre-release) as their 'test-version' override, and updates it to the new
     * full-release version if a match is found.
     */
    private void updateDependentTestVersions(String oldTestVersion, String newTestVersion, List<MetadataVersionsIndexEntry> entries) {
        for (int i = 0; i < entries.size(); i++) {
            MetadataVersionsIndexEntry entry = entries.get(i);

            // Check if the entry explicitly points to the old directory via 'test-version'
            if (oldTestVersion.equals(entry.testVersion())) {
                // Create a new entry with the updated test-version
                MetadataVersionsIndexEntry updatedEntry = new MetadataVersionsIndexEntry(
                        entry.latest(),
                        entry.override(),
                        entry.defaultFor(),
                        entry.metadataVersion(),
                        newTestVersion,
                        entry.testedVersions(),
                        entry.skippedVersions(),
                        entry.allowedPackages(),
                        entry.requires()
                );
                entries.set(i, updatedEntry);
            }
        }
    }
}
