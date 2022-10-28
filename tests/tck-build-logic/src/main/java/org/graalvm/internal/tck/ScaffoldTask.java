package org.graalvm.internal.tck;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.graalvm.internal.tck.model.MetadataIndexEntry;
import org.graalvm.internal.tck.model.TestIndexEntry;
import org.gradle.api.DefaultTask;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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

    @TaskAction
    void run() throws IOException {
        Coordinates coordinates = Coordinates.parse(this.coordinates);

        Path coordinatesMetadataRoot = getProject().file(replace("metadata/$group$/$artifact$", coordinates)).toPath();
        Path coordinatesMetadataVersionRoot = coordinatesMetadataRoot.resolve(coordinates.version());
        Path coordinatesTestRoot = getProject().file(replace("tests/src/$group$/$artifact$/$version$", coordinates)).toPath();

        checkExistingMetadata(coordinates, coordinatesMetadataRoot, coordinatesMetadataVersionRoot);

        // Metadata
        writeCoordinatesMetadataRootJson(coordinatesMetadataRoot, coordinates);
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

        objectMapper.writeValue(testIndex, entries);
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
        entries.add(new MetadataIndexEntry(directory, module, null));

        objectMapper.writeValue(metadataIndex, entries);
    }

    private void writeTestScaffold(Path coordinatesTestRoot, Coordinates coordinates) throws IOException {
        // build.gradle
        writeToFile(
                coordinatesTestRoot.resolve("build.gradle"),
                replace(loadResource("/scaffold/build.gradle.template"), coordinates)
        );

        // settings.gradle
        writeToFile(
                coordinatesTestRoot.resolve("settings.gradle"),
                replace(loadResource("/scaffold/settings.gradle.template"), coordinates)
        );

        // gradle.properties
        writeToFile(
                coordinatesTestRoot.resolve("gradle.properties"),
                replace(loadResource("/scaffold/gradle.properties.template"), coordinates)
        );

        // .gitignore
        writeToFile(
                coordinatesTestRoot.resolve(".gitignore"),
                replace(loadResource("/scaffold/.gitignore.template"), coordinates)
        );

        // src/test/java/
        writeToFile(
                coordinatesTestRoot.resolve(replace("src/test/java/$sanitizedGroup$/$sanitizedArtifact$/$capitalizedSanitizedArtifact$Test.java", coordinates)),
                replace(loadResource("/scaffold/Test.java.template"), coordinates)
        );
    }

    private void writeCoordinatesMetadataVersionJsons(Path metadataVersionRoot, Coordinates coordinates) throws IOException {
        // Root: metadata/$group$/$artifact$/$version$

        // index.json
        writeToFile(
                metadataVersionRoot.resolve("index.json"),
                replace(loadResource("/scaffold/metadataVersionIndex.json.template"), coordinates)
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
                replace(loadResource("/scaffold/serialization-config.json.template"), coordinates)
        );
    }

    private void writeCoordinatesMetadataRootJson(Path metadataRoot, Coordinates coordinates) throws IOException {
        // metadata/$group$/$artifact$/index.json
        writeToFile(
                metadataRoot.resolve("index.json"),
                replace(loadResource("/scaffold/metadataIndex.json.template"), coordinates)
        );
    }

    private String getEmptyJsonArray() {
        return "[]\n";
    }

    private String getEmptyJsonObject() {
        return "{}\n";
    }

    private String replace(String template, Coordinates coordinates) {
        return template
                .replace("$group$", coordinates.group())
                .replace("$sanitizedGroup$", coordinates.sanitizedGroup())
                .replace("$artifact$", coordinates.artifact())
                .replace("$sanitizedArtifact$", coordinates.sanitizedArtifact())
                .replace("$capitalizedSanitizedArtifact$", coordinates.capitalizedSanitizedArtifact())
                .replace("$version$", coordinates.version());
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


