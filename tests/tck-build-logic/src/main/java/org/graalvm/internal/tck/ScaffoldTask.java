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
import org.graalvm.internal.tck.model.DiscoveredArtifactMetadata;
import org.graalvm.internal.tck.model.LibraryLanguage;
import org.graalvm.internal.tck.model.MetadataVersionsIndexEntry;
import org.graalvm.internal.tck.utils.ArtifactMetadataDiscoveryUtils;
import org.graalvm.internal.tck.utils.CoordinateUtils;
import org.graalvm.internal.tck.utils.MetadataGenerationUtils;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Generates a scaffold for a new library.
 * <p>
 * Run with {@code gradle scaffold --coordinates com.example:library:1.0.0}.
 *
 * @author Moritz Halbritter
 */
@SuppressWarnings("unused")
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
        List<String> packageRoots = MetadataGenerationUtils.derivePackageRootsFromJar(getProject(), coordinates);
        DiscoveredArtifactMetadata discoveredMetadata = loadDiscoveredArtifactMetadata(coordinates);

        Path coordinatesMetadataRoot = getProject().file(CoordinateUtils.replace("metadata/$group$/$artifact$", coordinates)).toPath();
        Path coordinatesMetadataVersionRoot = coordinatesMetadataRoot.resolve(coordinates.version());
        Path coordinatesTestRoot = getProject().file(CoordinateUtils.replace("tests/src/$group$/$artifact$/$version$", coordinates)).toPath();
        Path coordinatesMetadataIndex = coordinatesMetadataRoot.resolve("index.json");
        boolean metadataRootExists = Files.exists(coordinatesMetadataIndex);
        boolean metadataEntryExists = metadataRootExists && !shouldAddNewMetadataEntry(coordinatesMetadataRoot, coordinates);
        List<MetadataVersionsIndexEntry> existingEntries = metadataRootExists
                ? objectMapper.readValue(coordinatesMetadataIndex.toFile(), new TypeReference<>() {})
                : List.of();
        LibraryLanguage entryLanguage = resolveEntryLanguage(discoveredMetadata, existingEntries);

        // Metadata
        checkExistingScaffold(coordinates, coordinatesMetadataVersionRoot, coordinatesTestRoot, metadataRootExists, metadataEntryExists);
        if (metadataRootExists) {
            if (metadataEntryExists) {
                getLogger().log(LogLevel.INFO, "Metadata index entry for {} already exists, keeping artifact index unchanged", coordinates);
            } else {
                if (!update) {
                    getLogger().log(LogLevel.INFO, "Artifact metadata root already exists for {}, appending new version entry", coordinates);
                }
                updateCoordinatesMetadataRootJson(coordinatesMetadataRoot, coordinates, packageRoots, discoveredMetadata, entryLanguage);
            }
        } else {
            writeCoordinatesMetadataRootJson(coordinatesMetadataRoot, coordinates, packageRoots, discoveredMetadata, entryLanguage);
        }

        writeCoordinatesMetadataVersionJsons(coordinatesMetadataVersionRoot, coordinates);

        // Tests
        writeTestScaffold(coordinatesTestRoot, coordinates, packageRoots, entryLanguage);

        System.out.printf("Generated metadata and test for %s%n", coordinates);
        System.out.printf("You can now use 'gradle test -Pcoordinates=%s' to run the tests%n", coordinates);
    }

    private void checkExistingScaffold(Coordinates coordinates, Path metadataVersionRoot, Path testRoot, boolean metadataRootExists, boolean metadataEntryExists) {
        if (force) {
            return;
        }

        boolean metadataVersionExists = Files.exists(metadataVersionRoot);
        boolean testRootExists = Files.exists(testRoot);
        if (metadataVersionExists || testRootExists || metadataEntryExists) {
            throw new IllegalStateException("Metadata for '%s' already exists! Use --force to overwrite existing metadata".formatted(coordinates));
        }

        if (metadataRootExists) {
            getLogger().log(LogLevel.INFO, "Artifact metadata root exists for {}, scaffold will add a new version", coordinates);
        }
    }

    private boolean shouldAddNewMetadataEntry(Path coordinatesMetadataRoot, Coordinates coordinates) throws IOException {
        File metadataIndex = coordinatesMetadataRoot.resolve("index.json").toFile();
        List<MetadataVersionsIndexEntry> entries = objectMapper.readValue(metadataIndex, new TypeReference<>() {});
        return entries.stream().noneMatch(e -> e.metadataVersion().equalsIgnoreCase(coordinates.version()));
    }

    private void writeTestScaffold(Path coordinatesTestRoot, Coordinates coordinates, List<String> packageRoots, LibraryLanguage language) throws IOException {
        ScaffoldFlavor scaffoldFlavor = ScaffoldFlavor.fromLanguage(language);
        // build.gradle
        writeToFile(
                coordinatesTestRoot.resolve("build.gradle"),
                CoordinateUtils.replace(loadResource(scaffoldFlavor.buildTemplatePath()), coordinates)
        );

        // user-code-filter.json
        writeToFile(
                coordinatesTestRoot.resolve("user-code-filter.json"),
                buildUserCodeFilter(packageRoots)
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

        // src/test/<language>/
        if (!skipTests) {
            writeToFile(
                    coordinatesTestRoot.resolve(CoordinateUtils.replace(scaffoldFlavor.testFileTemplatePath(), coordinates)),
                    CoordinateUtils.replace(loadResource(scaffoldFlavor.testTemplatePath()), coordinates)
            );
        }
    }

    private void writeCoordinatesMetadataVersionJsons(Path metadataVersionRoot, Coordinates coordinates) throws IOException {
        // Root: metadata/$group$/$artifact$/$version$

        // reachability-metadata.json
        writeToFile(
                metadataVersionRoot.resolve("reachability-metadata.json"),
                CoordinateUtils.replace(loadResource("/scaffold/reachability-metadata.json.template"), coordinates)
        );
    }

    private void writeCoordinatesMetadataRootJson(Path metadataRoot, Coordinates coordinates, List<String> packageRoots, DiscoveredArtifactMetadata discoveredMetadata, LibraryLanguage language) throws IOException {
        List<MetadataVersionsIndexEntry> entries = new ArrayList<>();
        entries.add(createIndexEntry(coordinates, packageRoots, discoveredMetadata, language, true));
        writeIndexFile(metadataRoot.resolve("index.json"), entries);
    }

    private void updateCoordinatesMetadataRootJson(Path metadataRoot, Coordinates coordinates, List<String> packageRoots, DiscoveredArtifactMetadata discoveredMetadata, LibraryLanguage language) throws IOException {
        if (!shouldAddNewMetadataEntry(metadataRoot, coordinates)) {
            throw new RuntimeException("Metadata for " + coordinates + " already exists!");
        }

        File metadataIndex = metadataRoot.resolve("index.json").toFile();
        List<MetadataVersionsIndexEntry> entries = objectMapper.readValue(metadataIndex, new TypeReference<>() {});

        // add new entry
        MetadataVersionsIndexEntry newEntry = createIndexEntry(coordinates, packageRoots, discoveredMetadata, language, false);

        entries.add(newEntry);

        updateLatestEntry(entries);

        entries.sort(Comparator.comparing(e -> VersionNumber.parse(e.metadataVersion())));
        writeIndexFile(metadataIndex.toPath(), entries);
    }

    private void setLatest(List<MetadataVersionsIndexEntry> list, int index, Boolean newValue) {
        MetadataVersionsIndexEntry oldEntry = list.get(index);
        list.set(index, new MetadataVersionsIndexEntry(
                newValue,
                oldEntry.override(),
                oldEntry.defaultFor(),
                oldEntry.metadataVersion(),
                oldEntry.testVersion(),
                oldEntry.sourceCodeUrl(),
                oldEntry.repositoryUrl(),
                oldEntry.testCodeUrl(),
                oldEntry.documentationUrl(),
                oldEntry.description(),
                oldEntry.language(),
                oldEntry.testedVersions(),
                oldEntry.skippedVersions(),
                oldEntry.allowedPackages(),
                oldEntry.requires()
        ));
    }

    private void updateLatestEntry(List<MetadataVersionsIndexEntry> entries) {
        int latestIndex = 0;
        VersionNumber latestVersion = VersionNumber.parse(entries.get(0).metadataVersion());
        for (int i = 1; i < entries.size(); i++) {
            VersionNumber nextVersion = VersionNumber.parse(entries.get(i).metadataVersion());
            if (latestVersion.compareTo(nextVersion) < 0) {
                latestVersion = nextVersion;
                latestIndex = i;
            }
        }

        for (int i = 0; i < entries.size(); i++) {
            setLatest(entries, i, i == latestIndex ? true : null);
        }
    }

    /// Builds user-code-filter.json content with an excludeClasses rule and one includeClasses per root.
    private String buildUserCodeFilter(List<String> packageRoots) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"rules\": [\n");
        sb.append("    {\n");
        sb.append("      \"excludeClasses\": \"**\"\n");
        sb.append("    }");
        for (String root : packageRoots) {
            sb.append(",\n    {\n");
            sb.append("      \"includeClasses\": \"").append(root).append(".**\"\n");
            sb.append("    }");
        }
        sb.append("\n  ]\n");
        sb.append("}\n");
        return sb.toString();
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

    private DiscoveredArtifactMetadata loadDiscoveredArtifactMetadata(Coordinates coordinates) throws IOException {
        Path discoveryFile = ArtifactMetadataDiscoveryUtils.discoveryFile(getProject().getLayout(), coordinates.toString());
        if (!Files.exists(discoveryFile)) {
            return null;
        }
        DiscoveredArtifactMetadata metadata = ArtifactMetadataDiscoveryUtils.readDiscoveryFile(discoveryFile);
        if (!coordinates.toString().equals(metadata.coordinates())) {
            throw new IllegalStateException("Discovery file " + discoveryFile + " has unexpected coordinates " + metadata.coordinates());
        }
        return metadata;
    }

    private LibraryLanguage resolveEntryLanguage(DiscoveredArtifactMetadata discoveredMetadata, List<MetadataVersionsIndexEntry> existingEntries) {
        if (discoveredMetadata != null && discoveredMetadata.language() != null) {
            return discoveredMetadata.language();
        }
        return existingEntries.stream()
                .filter(entry -> entry.language() != null)
                .max(Comparator.comparing(e -> VersionNumber.parse(e.metadataVersion())))
                .map(MetadataVersionsIndexEntry::language)
                .orElse(null);
    }

    private MetadataVersionsIndexEntry createIndexEntry(Coordinates coordinates, List<String> packageRoots, DiscoveredArtifactMetadata discoveredMetadata, LibraryLanguage language, boolean latest) {
        return new MetadataVersionsIndexEntry(
                latest ? Boolean.TRUE : null,
                null, // override
                null, // default-for
                coordinates.version(), // metadata-version
                null, // test-version
                discoveredMetadata == null ? null : discoveredMetadata.sourceCodeUrl(),
                discoveredMetadata == null ? null : discoveredMetadata.repositoryUrl(),
                discoveredMetadata == null ? null : discoveredMetadata.testCodeUrl(),
                discoveredMetadata == null ? null : discoveredMetadata.documentationUrl(),
                discoveredMetadata == null ? null : discoveredMetadata.description(),
                language,
                List.of(coordinates.version()),
                null, // skipped-versions
                packageRoots,
                null // requires
        );
    }

    private void writeIndexFile(Path path, List<MetadataVersionsIndexEntry> entries) throws IOException {
        DefaultPrettyPrinter prettyPrinter = new DefaultPrettyPrinter();
        prettyPrinter.indentArraysWith(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE);
        String json = objectMapper.writer(prettyPrinter).writeValueAsString(entries);
        if (!json.endsWith(System.lineSeparator())) {
            json = json + System.lineSeparator();
        }
        Files.createDirectories(Objects.requireNonNull(path.getParent()));
        Files.writeString(path, json, StandardCharsets.UTF_8);
    }

    private enum ScaffoldFlavor {
        JAVA("/scaffold/build.gradle.template", "/scaffold/Test.java.template", "src/test/java/$sanitizedGroup$/$sanitizedArtifact$/$capitalizedSanitizedArtifact$Test.java"),
        KOTLIN("/scaffold/build.gradle.kotlin.template", "/scaffold/Test.kt.template", "src/test/kotlin/$sanitizedGroup$/$sanitizedArtifact$/$capitalizedSanitizedArtifact$Test.kt"),
        SCALA2("/scaffold/build.gradle.scala2.template", "/scaffold/Test.scala.template", "src/test/scala/$sanitizedGroup$/$sanitizedArtifact$/$capitalizedSanitizedArtifact$Test.scala"),
        SCALA3("/scaffold/build.gradle.scala3.template", "/scaffold/Test.scala.template", "src/test/scala/$sanitizedGroup$/$sanitizedArtifact$/$capitalizedSanitizedArtifact$Test.scala");

        private final String buildTemplatePath;
        private final String testTemplatePath;
        private final String testFileTemplatePath;

        ScaffoldFlavor(String buildTemplatePath, String testTemplatePath, String testFileTemplatePath) {
            this.buildTemplatePath = buildTemplatePath;
            this.testTemplatePath = testTemplatePath;
            this.testFileTemplatePath = testFileTemplatePath;
        }

        static ScaffoldFlavor fromLanguage(LibraryLanguage language) {
            if (language == null) {
                return JAVA;
            }
            if (language.isKotlin()) {
                return KOTLIN;
            }
            if (language.isScala2()) {
                return SCALA2;
            }
            if (language.isScala3()) {
                return SCALA3;
            }
            if (language.isScala()) {
                throw new IllegalStateException("Unsupported Scala version for scaffold generation: " + language.version());
            }
            throw new IllegalStateException("Unsupported language for scaffold generation: " + language.name());
        }

        String buildTemplatePath() {
            return buildTemplatePath;
        }

        String testTemplatePath() {
            return testTemplatePath;
        }

        String testFileTemplatePath() {
            return testFileTemplatePath;
        }
    }
}
