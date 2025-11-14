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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("unused")
public abstract class TestedVersionUpdaterTask extends DefaultTask {
    /**
     * Identifies pre-release library versions by pattern matching against common pre-release suffixes.
     * <p>
     * A version is considered pre-release if its suffix (following the last '.' or '-') matches
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
     */
    private static final Pattern PRE_RELEASE_PATTERN = Pattern.compile(
            "(?i)^(\\d+(?:\\.\\d+)*)(?:[-.](alpha\\d*|beta\\d*|rc\\d*|cr\\d*|m\\d+|ea\\d*|b\\d+|\\d+|preview)(?:[-.].*)?)?$"
    );
    private static final Pattern FINAL_PATTERN = Pattern.compile("(?i)\\.Final$");

    @Option(option = "coordinates", description = "GAV coordinates of the library")
    void extractInformationFromCoordinates(String c) {
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

                entries.set(i, handlePreReleases(entry, newVersion, coordinatesMetadataIndex.toPath().getParent()));
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
     *   <li>If the newly added version is a full release (no pre-release label, or ending with ".Final"),
     *       all existing pre-releases of the same base version are removed from {@code testedVersions}.</li>
     *   <li>If the newly added version is itself a pre-release, no versions are removed.</li>
     *   <li>If the entry's {@code metadataVersion} is a pre-release of the same base version,
     *       it is updated to the new full release. The corresponding metadata and test directories are renamed accordingly.
     *       The {@code gradle.properties} file in the tests directory is updated to refer to the new version.</li>
     *   <li>Version parsing follows {@link #PRE_RELEASE_PATTERN} and treats ".Final" as a base version.</li>
     * </ul>
     */
    private MetadataVersionsIndexEntry handlePreReleases(MetadataVersionsIndexEntry entry, String newVersion, Path baseDir) throws IOException {
        // strip .Final if present
        String cleanedNewVersion = FINAL_PATTERN.matcher(newVersion).replaceAll("");
        Matcher newVersionMatcher = PRE_RELEASE_PATTERN.matcher(cleanedNewVersion);
        if (!newVersionMatcher.matches()) {
            throw new IllegalArgumentException("New version is not valid: " + newVersion);
        }

        String newBaseVersion = newVersionMatcher.group(1);
        String newPreReleaseLabel = newVersionMatcher.group(2); // null = full release

        // Only remove old pre-releases if the new version is a full release of the same version
        if (newPreReleaseLabel == null) {
            entry.testedVersions().removeIf(v -> {
                // strip .Final for comparison
                String cleanedV = FINAL_PATTERN.matcher(v).replaceAll("");
                Matcher m = PRE_RELEASE_PATTERN.matcher(cleanedV);
                return m.matches() && m.group(2) != null && newBaseVersion.equals(m.group(1));
            });

            // Update metadata-version if it was a pre-release of the new version
            Matcher metadataMatcher = PRE_RELEASE_PATTERN.matcher(FINAL_PATTERN.matcher(entry.metadataVersion()).replaceAll(""));
            if (metadataMatcher.matches() && metadataMatcher.group(2) != null && newBaseVersion.equals(metadataMatcher.group(1))) {
                String oldMetadataVersion = entry.metadataVersion();

                // Rename the metadata directory
                Path oldDir = baseDir.resolve(oldMetadataVersion);
                Path newDir = baseDir.resolve(newVersion);
                if (Files.exists(oldDir)) {
                    Files.move(oldDir, newDir);
                }

                updateTests(baseDir, entry, oldMetadataVersion, newVersion);

                // Return new record with updated metadata-version
                return new MetadataVersionsIndexEntry(
                        entry.latest(),
                        entry.override(),
                        entry.module(),
                        entry.defaultFor(),
                        newVersion,
                        entry.testedVersions()
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
        Path testsRoot = metadataBaseDir.getParent()
                .getParent()
                .getParent()
                .resolve("tests/src")
                .resolve(entry.module().replace(":", "/"));

        Path oldTestDir = testsRoot.resolve(oldVersion);
        Path newTestDir = testsRoot.resolve(newVersion);

        // Rename the tests directory
        if (Files.exists(oldTestDir)) {
            Files.move(oldTestDir, newTestDir);
        } else {
            return;
        }

        // Update gradle.properties inside the renamed directory
        updateGradleProperties(newTestDir, entry, newVersion);

        // Update global tests index.json
        updateTestsIndexJson(metadataBaseDir.getParent().getParent().getParent().resolve("tests/src/index.json"), entry, oldVersion, newVersion);
    }

    /**
     * Updates {@code gradle.properties} inside a given test directory to reflect the new version.
     */
    private void updateGradleProperties(Path testDir, MetadataVersionsIndexEntry entry, String newVersion) throws IOException {
        Path gradleProps = testDir.resolve("gradle.properties");
        if (!Files.exists(gradleProps)) {
            return;
        }

        List<String> lines = Files.readAllLines(gradleProps);
        List<String> updated = new ArrayList<>();
        for (String line : lines) {
            if (line.startsWith("library.version")) {
                updated.add("library.version = " + newVersion);
            } else if (line.startsWith("metadata.dir")) {
                updated.add("metadata.dir = " + entry.module().replace(":", "/") + "/" + newVersion + "/");
            } else {
                updated.add(line);
            }
        }

        Files.write(gradleProps, updated);
    }

    /**
     * Updates the global tests index.json file to reflect the renamed test directory and new version.
     */
    @SuppressWarnings("unchecked")
    private void updateTestsIndexJson(Path indexJson, MetadataVersionsIndexEntry entry, String oldVersion, String newVersion) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        List<Map<String, Object>> entries = objectMapper.readValue(indexJson.toFile(), new TypeReference<>() {});

        // Compute the old test-project-path for matching
        String oldTestProjectPath = entry.module().replace(":", "/") + "/" + oldVersion;
        String newTestProjectPath = entry.module().replace(":", "/") + "/" + newVersion;

        for (Map<String, Object> e : entries) {
            String path = (String) e.get("test-project-path");
            if (!oldTestProjectPath.equals(path)) {
                continue;
            }

            // Update the test-project-path
            e.put("test-project-path", newTestProjectPath);

            // Update the versions array inside libraries
            List<Map<String, Object>> libs = (List<Map<String, Object>>) e.get("libraries");
            for (Map<String, Object> lib : libs) {
                List<String> versions = (List<String>) lib.get("versions");
                versions.clear();
                versions.add(newVersion);
            }

            break;
        }

        DefaultPrettyPrinter prettyPrinter = new DefaultPrettyPrinter();
        prettyPrinter.indentArraysWith(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE);
        String json = objectMapper.writer(prettyPrinter).writeValueAsString(entries);
        if (!json.endsWith("\n")) json += System.lineSeparator();
        Files.writeString(indexJson, json, StandardCharsets.UTF_8);
    }
}
