package org.graalvm.internal.tck;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.graalvm.internal.tck.model.MetadataIndexEntry;
import org.graalvm.internal.tck.model.MetadataVersionsIndexEntry;
import org.graalvm.internal.tck.model.TestIndexEntry;
import org.gradle.api.DefaultTask;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.gradle.util.internal.VersionNumber;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/**
 * Generates a scaffold for a new library.
 * <p>
 * Run with {@code gradle scaffold --coordinates com.example:library:1.0.0}.
 *
 * @author Moritz Halbritter
 */
class ScaffoldTask extends DefaultTask {
    private final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private String coordinates;

    private boolean force;
    private boolean update;
    private boolean skipTests;

    public ScaffoldTask() {
    }

    @Option(option = "coordinates", description = "Coordinates in the form of group:artifact:version")
    void setCoordinates(String coordinates) {
        this.coordinates = coordinates;
    }

    @Input
    String getCoordinates() {
        return coordinates;
    }

    @Option(option = "force", description = "Force overwrite of existing metadata")
    void setForce(boolean force) {
        this.force = force;
    }


    @Option(option = "update", description = "Add metadata for new version of library that already exists in the repository")
    void setUpdate(boolean update) {
        this.update = update;
    }

    @Option(option = "skipTests", description = "Skip adding test stubs")
    void setSkipTests(boolean skip) {
        this.skipTests = skip;
    }

    @TaskAction
    void run() throws IOException {
        Coordinates coordinates = Coordinates.parse(this.coordinates);

        Path coordinatesMetadataRoot = getProject().file(CoordinateUtils.replace("metadata/$group$/$artifact$", coordinates)).toPath();
        Path coordinatesMetadataVersionRoot = coordinatesMetadataRoot.resolve(coordinates.version());
        Path coordinatesTestRoot = getProject().file(CoordinateUtils.replace("tests/src/$group$/$artifact$/$version$", coordinates)).toPath();

        // Metadata
        if (!update) {
            checkExistingMetadata(coordinates, coordinatesMetadataRoot, coordinatesMetadataVersionRoot);
            writeCoordinatesMetadataRootJson(coordinatesMetadataRoot, coordinates);
        } else {
            updateCoordinatesMetadataRootJson(coordinatesMetadataRoot, coordinates);
        }

        writeCoordinatesMetadataVersionJsons(coordinatesMetadataVersionRoot, coordinates);
        addToMetadataIndexJson(coordinates);

        // Tests
        writeTestScaffold(coordinatesTestRoot, coordinates);
        addToTestIndexJson(coordinates);

        System.out.printf("Generated metadata and test for %s%n", coordinates);
        System.out.printf("You can now use 'gradle test -Pcoordinates=%s' to run the tests%n", coordinates);
    }

    private void checkExistingMetadata(Coordinates coordinates, Path metadataVersionRoot, Path coordinatesMetadataVersionRoot) {
        if (force) {
            return;
        }

        // metadata/$group/$artifact/index.json
        if (Files.exists(metadataVersionRoot.resolve("index.json"))) {
            throw new IllegalStateException("Metadata for '%s:%s' already exists! Use --force to overwrite existing metadata".formatted(coordinates.group(), coordinates.artifact()));
        }

        // metadata/$group/$artifact/$version/index.json
        if (Files.exists(coordinatesMetadataVersionRoot.resolve("index.json"))) {
            throw new IllegalStateException("Metadata for '%s' already exists! Use --force to overwrite existing metadata".formatted(coordinates));
        }
    }

    private boolean shouldAddNewMetadataEntry(Path coordinatesMetadataRoot, Coordinates coordinates) throws IOException {
        String newModule = coordinates.group() + ":" + coordinates.artifact();
        File metadataIndex = coordinatesMetadataRoot.resolve("index.json").toFile();
        List<MetadataVersionsIndexEntry> entries = objectMapper.readValue(metadataIndex, new TypeReference<>() {});
        return entries.stream().noneMatch(e -> e.module().equalsIgnoreCase(newModule) && e.metadataVersion().equalsIgnoreCase(coordinates.version()));
    }


    private void addToTestIndexJson(Coordinates coordinates) throws IOException {
        File testIndex = getProject().file("tests/src/index.json");
        List<TestIndexEntry> entries = objectMapper.readValue(testIndex, new TypeReference<>() {
        });

        String testProjectPath = coordinates.group() + "/" + coordinates.artifact() + "/" + coordinates.version();

        // Look for existing entry
        for (TestIndexEntry entry : entries) {
            if (entry.testProjectPath().equals(testProjectPath)) {
                getLogger().debug("Found {} in {}", testProjectPath, testIndex);
                return;
            }
        }

        // Entry is not in index.json, add it
        getLogger().debug("Did not find {} in {}, adding it", testProjectPath, testIndex);
        entries.add(new TestIndexEntry(testProjectPath, List.of(
                new TestIndexEntry.LibraryEntry(coordinates.group() + ":" + coordinates.artifact(), List.of(coordinates.version()))
        )));

        List<TestIndexEntry> sortedEntries = entries.stream()
                .sorted(Comparator.comparing(TestIndexEntry::testProjectPath))
                .toList();

        objectMapper.writeValue(testIndex, sortedEntries);
    }

    private void addToMetadataIndexJson(Coordinates coordinates) throws IOException {
        File metadataIndex = getProject().file("metadata/index.json");
        List<MetadataIndexEntry> entries = objectMapper.readValue(metadataIndex, new TypeReference<>() {
        });

        String module = coordinates.group() + ":" + coordinates.artifact();
        String directory = coordinates.group() + "/" + coordinates.artifact();

        // Look for existing entry
        for (MetadataIndexEntry entry : entries) {
            if (entry.module().equals(module)) {
                getLogger().debug("Found {} in {}", module, metadataIndex);
                return;
            }
        }

        // Entry is not in index.json, add it
        getLogger().debug("Did not find {} in {}, adding it", module, metadataIndex);
        entries.add(new MetadataIndexEntry(directory, module, null, List.of(coordinates.group())));

        List<MetadataIndexEntry> sortedEntries = entries.stream()
                .sorted(Comparator.comparing(MetadataIndexEntry::module))
                .toList();

        objectMapper.writeValue(metadataIndex, sortedEntries);
    }

    private void writeTestScaffold(Path coordinatesTestRoot, Coordinates coordinates) throws IOException {
        // build.gradle
        writeToFile(
                coordinatesTestRoot.resolve("build.gradle"),
                CoordinateUtils.replace(loadResource("/scaffold/build.gradle.template"), coordinates)
        );

        // settings.gradle
        writeToFile(
                coordinatesTestRoot.resolve("settings.gradle"),
                CoordinateUtils.replace(loadResource("/scaffold/settings.gradle.template"), coordinates)
        );

        // gradle.properties
        writeToFile(
                coordinatesTestRoot.resolve("gradle.properties"),
                CoordinateUtils.replace(loadResource("/scaffold/gradle.properties.template"), coordinates)
        );

        // .gitignore
        writeToFile(
                coordinatesTestRoot.resolve(".gitignore"),
                CoordinateUtils.replace(loadResource("/scaffold/.gitignore.template"), coordinates)
        );

        // src/test/java/
        if (!skipTests) {
            writeToFile(
                    coordinatesTestRoot.resolve(CoordinateUtils.replace("src/test/java/$sanitizedGroup$/$sanitizedArtifact$/$capitalizedSanitizedArtifact$Test.java", coordinates)),
                    CoordinateUtils.replace(loadResource("/scaffold/Test.java.template"), coordinates)
            );
        }
    }

    private void writeCoordinatesMetadataVersionJsons(Path metadataVersionRoot, Coordinates coordinates) throws IOException {
        // Root: metadata/$group$/$artifact$/$version$

        // index.json
        writeToFile(
                metadataVersionRoot.resolve("index.json"),
                CoordinateUtils.replace(loadResource("/scaffold/metadataVersionIndex.json.template"), coordinates)
        );

        // jni-config.json
        writeToFile(
                metadataVersionRoot.resolve("jni-config.json"),
                getEmptyJsonArray()
        );

        // proxy-config.json
        writeToFile(
                metadataVersionRoot.resolve("proxy-config.json"),
                getEmptyJsonArray()
        );

        // reflect-config.json
        writeToFile(
                metadataVersionRoot.resolve("reflect-config.json"),
                getEmptyJsonArray()
        );

        // resource-config.json
        writeToFile(
                metadataVersionRoot.resolve("resource-config.json"),
                getEmptyJsonObject()
        );

        // serialization-config.json
        writeToFile(
                metadataVersionRoot.resolve("serialization-config.json"),
                CoordinateUtils.replace(loadResource("/scaffold/serialization-config.json.template"), coordinates)
        );
    }

    private void writeCoordinatesMetadataRootJson(Path metadataRoot, Coordinates coordinates) throws IOException {
        // metadata/$group$/$artifact$/index.json
        writeToFile(
                metadataRoot.resolve("index.json"),
                CoordinateUtils.replace(loadResource("/scaffold/metadataIndex.json.template"), coordinates)
        );
    }

    private void updateCoordinatesMetadataRootJson(Path metadataRoot, Coordinates coordinates) throws IOException {
        if (!shouldAddNewMetadataEntry(metadataRoot, coordinates)) {
            throw new RuntimeException("Metadata for " + coordinates + " already exists!");
        }

        File metadataIndex = metadataRoot.resolve("index.json").toFile();
        List<MetadataVersionsIndexEntry> entries = objectMapper.readValue(metadataIndex, new TypeReference<>() {});

        // add new entry
        MetadataVersionsIndexEntry newEntry = new MetadataVersionsIndexEntry(null,
                null,
                coordinates.group() + ":" + coordinates.artifact(),
                null,
                coordinates.version(),
                List.of(coordinates.version()));

        entries.add(newEntry);

        // determine updates
        int previousLatest = -1;
        int newLatest = -1;
        VersionNumber latestVersion = VersionNumber.parse(entries.get(0).metadataVersion());
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).latest() != null && entries.get(i).latest()) {
                previousLatest = i;
            }

            VersionNumber nextVersion = VersionNumber.parse(entries.get(i).metadataVersion());
            if (latestVersion.compareTo(nextVersion) < 0){
                newLatest = i;
            }
        }

        if (previousLatest != -1) {
            setLatest(entries, previousLatest, null);
        }

        if (newLatest != -1) {
            setLatest(entries, newLatest, true);
        }

        entries.sort(Comparator.comparing(MetadataVersionsIndexEntry::module));
        objectMapper.writeValue(metadataIndex, entries);
    }

    private void setLatest( List<MetadataVersionsIndexEntry> list, int index, Boolean newValue) {
        MetadataVersionsIndexEntry oldEntry = list.remove(index);
        list.add(new MetadataVersionsIndexEntry(newValue, oldEntry.override(), oldEntry.module(), oldEntry.defaultFor(), oldEntry.metadataVersion(), oldEntry.testedVersions()));
    }

    private String getEmptyJsonArray() {
        return "[]\n";
    }

    private String getEmptyJsonObject() {
        return "{}\n";
    }

    private String loadResource(String name) throws IOException {
        try (InputStream stream = ScaffoldTask.class.getResourceAsStream(name)) {
            if (stream == null) {
                throw new IOException("Resource '%s' not found".formatted(name));
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void writeToFile(Path path, String content) throws IOException {
        if (getLogger().isEnabled(LogLevel.DEBUG)) {
            getLogger().debug("Writing to file {}", path.toAbsolutePath());
        }
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }
}


