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
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.file.ProjectLayout;
import org.gradle.process.ExecOperations;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Static utility class for operations shared by metadata-generation tasks.
 */
public final class MetadataGenerationUtils {

    public static final String BUILD_FILE = "build.gradle";
    private static final String USER_CODE_FILTER_FILE = "user-code-filter.json";

    private static final ObjectMapper objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private MetadataGenerationUtils() {
    }

    /**
     * Resolves the binary JAR for the given coordinates and derives minimal package roots.
     */
    public static List<String> derivePackageRootsFromJar(Project project, Coordinates coordinates) throws IOException {
        DependencyHandler dependencies = project.getDependencies();
        Configuration configuration = project.getConfigurations().detachedConfiguration(
                dependencies.create(coordinates.group() + ":" + coordinates.artifact() + ":" + coordinates.version())
        );
        configuration.setTransitive(false);

        List<Path> jars = configuration.resolve().stream()
                .map(file -> file.toPath().toAbsolutePath())
                .toList();

        if (jars.isEmpty()) {
            throw new GradleException("Failed to resolve JAR for " + coordinates);
        }

        Set<String> classNames = JarUtils.loadClassNames(jars);
        List<String> roots = JarUtils.derivePackageRoots(classNames);

        if (roots.isEmpty()) {
            project.getLogger().log(LogLevel.WARN, "No packages found in JAR for {}, falling back to group ID", coordinates);
            return List.of(coordinates.group());
        }

        project.getLogger().log(LogLevel.INFO, "Derived package roots for {}: {}", coordinates, roots);
        return roots;
    }

    /**
     * Returns true if the tests build.gradle already contains a native-image agent block.
     */
    public static boolean hasAgentConfigBlock(Path testsDirectory) throws IOException {
        Path buildFilePath = testsDirectory.resolve(BUILD_FILE);
        if (!Files.isRegularFile(buildFilePath)) {
            throw new RuntimeException("Cannot check agent block in " + buildFilePath + ". Please check if a " + BUILD_FILE + " exists on that location.");
        }
        String buildGradle = Files.readString(buildFilePath, StandardCharsets.UTF_8);
        return Pattern.compile("(?s)\\bagent\\s*\\{").matcher(buildGradle).find();
    }

    /**
     * Creates a user-code-filter.json file including the given packages (and excluding all others),
     * used to restrict metadata generation to user code.
     */
    public static void addUserCodeFilterFile(Path testsDirectory, List<String> packages) throws IOException {
        GeneralUtils.printInfo("Generating " + USER_CODE_FILTER_FILE);
        List<Map<String, String>> filterFileRules = new ArrayList<>();

        // add exclude classes
        filterFileRules.add(Map.of("excludeClasses", "**"));

        // add include classes
        packages.forEach(p -> filterFileRules.add(Map.of("includeClasses", p + ".**")));

        DefaultPrettyPrinter prettyPrinter = new DefaultPrettyPrinter();
        prettyPrinter.indentArraysWith(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE);
        Path out = testsDirectory.resolve(USER_CODE_FILTER_FILE);
        String json = objectMapper.writer(prettyPrinter).writeValueAsString(Map.of("rules", filterFileRules));
        if (!json.endsWith(System.lineSeparator())) {
            json = json + System.lineSeparator();
        }
        Files.writeString(out, json, StandardCharsets.UTF_8);
    }

    /**
     * Appends the agent configuration block to the build.gradle in the tests directory
     * if it does not already exist.
     */
    public static void addAgentConfigBlock(Path testsDirectory) throws IOException {
        Path buildFilePath = testsDirectory.resolve(BUILD_FILE);
        GeneralUtils.printInfo("Configuring agent block in: " + BUILD_FILE);

        // Skip generation if agent block already exists
        boolean hasAgentBlock = hasAgentConfigBlock(testsDirectory);
        if (hasAgentBlock) {
            GeneralUtils.printInfo("Agent block already present in: " + BUILD_FILE + " - skipping");
            return;
        }

        try (InputStream stream = MetadataGenerationUtils.class.getResourceAsStream("/contributing/agent.template")) {
            if (stream == null) {
                throw new RuntimeException("Cannot find template for the graalvm configuration block");
            }

            String content = System.lineSeparator() + (new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            GeneralUtils.writeToFile(buildFilePath, content, StandardOpenOption.APPEND);
        }
    }

    /**
     * Runs Gradle tasks to generate metadata using the agent and then copies
     * the results into the computed metadata directory for the given coordinates.
     */
    public static void collectMetadata(ExecOperations execOps, Path testsDirectory, ProjectLayout layout,  String coordinates, Path gradlew) {
        Path metadataDirectory = GeneralUtils.computeMetadataDirectory(layout, coordinates);

        GeneralUtils.printInfo("Generating metadata");
        GeneralUtils.invokeCommand(execOps, gradlew.toString(), List.of("-Pagent", "test"), "Cannot generate metadata", testsDirectory);

        GeneralUtils.printInfo("Performing metadata copy");
        GeneralUtils.invokeCommand(execOps, gradlew + " metadataCopy --task test --dir " + metadataDirectory, "Cannot perform metadata copy", testsDirectory);
    }

    /**
     * Runs Gradle tasks to generate metadata using the agent with a specific GVM_TCK_LV
     * and then copies the results into the computed metadata directory for the given coordinates.
     */
    public static void collectMetadata(ExecOperations execOps, Path testsDirectory, ProjectLayout layout, String coordinates, Path gradlew, String gvmTckLv) {
        Path metadataDirectory = GeneralUtils.computeMetadataDirectory(layout, coordinates);

        Map<String, String> env = Map.of("GVM_TCK_LV", gvmTckLv);

        GeneralUtils.printInfo("Generating metadata");
        GeneralUtils.invokeCommand(execOps, gradlew.toString(), List.of("-Pagent", "test"), env, "Cannot generate metadata", testsDirectory);

        GeneralUtils.printInfo("Performing metadata copy");
        GeneralUtils.invokeCommand(execOps, gradlew.toString(), List.of("metadataCopy", "--task", "test", "--dir", metadataDirectory.toString()), env, "Cannot perform metadata copy", testsDirectory);
    }

    /**
     * Marks the library version identified by {@code newCoords} as the {@code latest} entry
     * within its corresponding {@code index.json}.
     */
    public static void makeVersionLatestInIndexJson(ProjectLayout layout, Coordinates newCoords, String testVersion) throws IOException {
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
        // Remove 'latest' flag from any existing latest entry
        for (int i = 0; i < entries.size(); i++) {
            MetadataVersionsIndexEntry entry = entries.get(i);
            if (Boolean.TRUE.equals(entry.latest())) {
                entries.set(i, new MetadataVersionsIndexEntry(
                        null, // latest removed
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
                        entry.requires()
                ));
                latestAllowedPackages = entry.allowedPackages();
                latestRequires = entry.requires();
                latestLanguage = entry.language();
            }
        }

        // Add the new entry and mark it as latest
        List<String> testedVersions = new ArrayList<>();
        testedVersions.add(newCoords.version());
        MetadataVersionsIndexEntry newEntry = new MetadataVersionsIndexEntry(
                Boolean.TRUE, // latest
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
                latestRequires // requires
        );
        entries.addFirst(newEntry);

        DefaultPrettyPrinter prettyPrinter = new DefaultPrettyPrinter();
        prettyPrinter.indentArraysWith(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE);
        String json = objectMapper.writer(prettyPrinter).writeValueAsString(entries);
        if (!json.endsWith(System.lineSeparator())) {
            json = json + System.lineSeparator();
        }
        Files.writeString(indexFile.toPath(), json, java.nio.charset.StandardCharsets.UTF_8);
    }
}
