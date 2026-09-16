/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.utils;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.graalvm.internal.tck.Coordinates;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.attributes.Bundling;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.attributes.Usage;
import org.gradle.api.attributes.java.TargetJvmEnvironment;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.logging.LogLevel;
import org.gradle.process.ExecOperations;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Static utility class for operations shared by metadata-generation tasks.
 */
public final class MetadataGenerationUtils {

    public static final String BUILD_FILE = "build.gradle";
    private static final String USER_CODE_FILTER_FILE = "user-code-filter.json";
    private static final List<String> TEST_SOURCE_SETS = List.of("src/test/java", "src/test/kotlin", "src/test/groovy", "src/test/scala");
    private static final List<String> TEST_SOURCE_EXTENSIONS = List.of(".java", ".kt", ".groovy", ".scala");
    private static final List<String> TEST_SOURCE_MARKERS = List.of(
            "@Test",
            "org.junit.",
            "org.testng.",
            "spock.lang.",
            "org.scalatest."
    );
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
        configuration.attributes(attributes -> {
            attributes.attribute(
                    Category.CATEGORY_ATTRIBUTE,
                    project.getObjects().named(Category.class, Category.LIBRARY)
            );
            attributes.attribute(
                    Bundling.BUNDLING_ATTRIBUTE,
                    project.getObjects().named(Bundling.class, Bundling.EXTERNAL)
            );
            attributes.attribute(
                    LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                    project.getObjects().named(LibraryElements.class, LibraryElements.JAR)
            );
            attributes.attribute(
                    Usage.USAGE_ATTRIBUTE,
                    project.getObjects().named(Usage.class, Usage.JAVA_RUNTIME)
            );
            attributes.attribute(
                    TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE,
                    project.getObjects().named(TargetJvmEnvironment.class, TargetJvmEnvironment.STANDARD_JVM)
            );
        });

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
     * Creates a user-code-filter.json file including the given library packages plus any
     * packages discovered under the tests' source roots (so the agent records events whose
     * call stack only contains test + JDK frames, e.g. test-driven serialization).
     */
    public static void addUserCodeFilterFile(Path testsDirectory, List<String> packages) throws IOException {
        GeneralUtils.printInfo("Generating " + USER_CODE_FILTER_FILE);
        List<Map<String, String>> filterFileRules = new ArrayList<>();

        // add exclude classes
        filterFileRules.add(Map.of("excludeClasses", "**"));

        // include library packages
        Set<String> seen = new LinkedHashSet<>();
        for (String p : packages) {
            if (seen.add(p)) {
                filterFileRules.add(Map.of("includeClasses", p + ".**"));
            }
        }

        // include test packages so the agent records events triggered solely from test code
        for (String p : discoverTestPackages(testsDirectory)) {
            if (seen.add(p)) {
                filterFileRules.add(Map.of("includeClasses", p + ".**"));
            }
        }

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
     * Walks the conventional test source roots and returns the package names they contain.
     */
    public static Set<String> discoverTestPackages(Path testsDirectory) throws IOException {
        Set<String> packages = new LinkedHashSet<>();
        for (String sourceSet : TEST_SOURCE_SETS) {
            Path sourceRoot = testsDirectory.resolve(sourceSet);
            if (!Files.isDirectory(sourceRoot)) {
                continue;
            }
            try (var pathStream = Files.walk(sourceRoot)) {
                pathStream.filter(Files::isRegularFile).forEach(path -> {
                    Path relativeParent = sourceRoot.relativize(path).getParent();
                    if (relativeParent == null) {
                        return;
                    }
                    String packageName = relativeParent.toString().replace('/', '.').replace('\\', '.');
                    if (!packageName.isBlank()) {
                        packages.add(packageName);
                    }
                });
            }
        }
        return packages;
    }

    /**
     * Returns packages that are clear test owners for metadata splitting.
     * Dependency API stubs may live under src/test in their real packages, but
     * they are not test-only merely because a generated test provided a stub.
     */
    public static Set<String> discoverTestOnlyMetadataPackages(Path testsDirectory) throws IOException {
        Set<String> packages = new LinkedHashSet<>();
        for (String sourceSet : TEST_SOURCE_SETS) {
            Path sourceRoot = testsDirectory.resolve(sourceSet);
            if (!Files.isDirectory(sourceRoot)) {
                continue;
            }
            try (var pathStream = Files.walk(sourceRoot)) {
                for (Path path : pathStream.filter(Files::isRegularFile).toList()) {
                    if (!isTestOwnedSource(path)) {
                        continue;
                    }
                    Path relativeParent = sourceRoot.relativize(path).getParent();
                    if (relativeParent == null) {
                        continue;
                    }
                    String packageName = relativeParent.toString().replace('/', '.').replace('\\', '.');
                    if (!packageName.isBlank()) {
                        packages.add(packageName);
                    }
                }
            }
        }
        return packages;
    }

    private static boolean isTestOwnedSource(Path path) throws IOException {
        String fileName = path.getFileName().toString();
        if (TEST_SOURCE_EXTENSIONS.stream().noneMatch(fileName::endsWith)) {
            return false;
        }
        String source = Files.readString(path, StandardCharsets.UTF_8);
        return TEST_SOURCE_MARKERS.stream().anyMatch(source::contains);
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
    public static void collectMetadata(ExecOperations execOps, Path testsDirectory, ProjectLayout layout, String coordinates, Path gradlew) {
        collectMetadataWithCoverageSuite(execOps, testsDirectory, layout, coordinates, gradlew, false);
    }

    public static void collectMetadataWithCoverageSuite(
            ExecOperations execOps,
            Path testsDirectory,
            ProjectLayout layout,
            String coordinates,
            Path gradlew,
            boolean includeCodeCoverageSuite
    ) {
        Path metadataDirectory = GeneralUtils.computeMetadataDirectory(layout, coordinates);
        try {
            Path agentMetadataDirectory = Files.createTempDirectory("generate-metadata-agent-");
            Path mergedMetadataDirectory = Files.createTempDirectory("generate-metadata-merged-");
            try {
                collectMetadata(
                        execOps, testsDirectory, layout, coordinates, gradlew,
                        agentMetadataDirectory, includeCodeCoverageSuite
                );
                mergeMetadataIntoDurableDirectory(
                        execOps, layout, gradlew, metadataDirectory,
                        agentMetadataDirectory, mergedMetadataDirectory
                );
            } finally {
                deleteRecursively(agentMetadataDirectory);
                deleteRecursively(mergedMetadataDirectory);
            }
        } catch (IOException e) {
            throw new RuntimeException("Cannot prepare metadata generation staging directories", e);
        }
    }

    /**
     * Runs Gradle tasks to generate metadata using the agent and copies
     * the results into the requested output directory without durable merging.
     */
    public static void collectMetadata(ExecOperations execOps, Path testsDirectory, ProjectLayout layout, String coordinates, Path gradlew, Path metadataDirectory) {
        collectMetadata(execOps, testsDirectory, layout, coordinates, gradlew, metadataDirectory, false);
    }

    public static void collectMetadata(
            ExecOperations execOps,
            Path testsDirectory,
            ProjectLayout layout,
            String coordinates,
            Path gradlew,
            Path metadataDirectory,
            boolean includeCodeCoverageSuite
    ) {
        Path resolvedMetadataDirectory = resolveMetadataDirectory(layout, metadataDirectory);
        List<String> testArguments = new ArrayList<>(List.of("-Pagent"));
        if (includeCodeCoverageSuite) {
            testArguments.add("-PincludeCodeCoverageSuite=true");
        }
        testArguments.add("test");

        GeneralUtils.printInfo("Generating metadata");
        GeneralUtils.invokeCommand(
                execOps, gradlew.toString(), testArguments,
                "Cannot generate metadata", testsDirectory
        );

        GeneralUtils.printInfo("Performing metadata copy");
        GeneralUtils.invokeCommand(execOps, gradlew.toString(), List.of("metadataCopy", "--task", "test", "--dir", resolvedMetadataDirectory.toString()), "Cannot perform metadata copy", testsDirectory);
    }

    /**
     * Runs Gradle tasks to generate metadata using the agent with a specific GVM_TCK_LV
     * and then copies the results into the computed metadata directory for the given coordinates.
     */
    public static void collectMetadata(ExecOperations execOps, Path testsDirectory, ProjectLayout layout, String coordinates, Path gradlew, String gvmTckLv) {
        Path metadataDirectory = GeneralUtils.computeMetadataDirectory(layout, coordinates);
        try {
            Path agentMetadataDirectory = Files.createTempDirectory("generate-metadata-agent-");
            Path mergedMetadataDirectory = Files.createTempDirectory("generate-metadata-merged-");
            try {
                collectMetadata(execOps, testsDirectory, layout, coordinates, gradlew, gvmTckLv, agentMetadataDirectory);
                mergeMetadataIntoDurableDirectory(execOps, layout, gradlew, metadataDirectory, agentMetadataDirectory, mergedMetadataDirectory);
            } finally {
                deleteRecursively(agentMetadataDirectory);
                deleteRecursively(mergedMetadataDirectory);
            }
        } catch (IOException e) {
            throw new RuntimeException("Cannot prepare metadata generation staging directories", e);
        }
    }

    /**
     * Runs Gradle tasks to generate metadata using the agent with a specific GVM_TCK_LV
     * and copies the results into the requested output directory without durable merging.
     */
    public static void collectMetadata(ExecOperations execOps, Path testsDirectory, ProjectLayout layout, String coordinates, Path gradlew, String gvmTckLv, Path metadataDirectory) {
        Path resolvedMetadataDirectory = resolveMetadataDirectory(layout, metadataDirectory);

        Map<String, String> env = Map.of("GVM_TCK_LV", gvmTckLv);

        GeneralUtils.printInfo("Generating metadata");
        GeneralUtils.invokeCommand(execOps, gradlew.toString(), List.of("-Pagent", "test"), env, "Cannot generate metadata", testsDirectory);

        GeneralUtils.printInfo("Performing metadata copy");
        GeneralUtils.invokeCommand(execOps, gradlew.toString(), List.of("metadataCopy", "--task", "test", "--dir", resolvedMetadataDirectory.toString()), env, "Cannot perform metadata copy", testsDirectory);
    }

    private static Path resolveMetadataDirectory(ProjectLayout layout, Path metadataDirectory) {
        if (metadataDirectory.isAbsolute()) {
            return metadataDirectory;
        }
        return layout.getProjectDirectory().getAsFile().toPath().resolve(metadataDirectory).normalize();
    }

    private static void mergeMetadataIntoDurableDirectory(
            ExecOperations execOps,
            ProjectLayout layout,
            Path gradlew,
            Path durableMetadataDirectory,
            Path agentMetadataDirectory,
            Path mergedMetadataDirectory
    ) throws IOException {
        List<Path> inputDirectories = Files.isRegularFile(durableMetadataDirectory.resolve("reachability-metadata.json"))
                ? List.of(durableMetadataDirectory, agentMetadataDirectory)
                : List.of(agentMetadataDirectory);
        GeneralUtils.printInfo("Merging generated metadata");
        GeneralUtils.invokeCommand(
                execOps,
                gradlew.toString(),
                List.of(
                        "mergeNativeTraceMetadata",
                        "-PinputDirs=" + String.join(",", inputDirectories.stream().map(Path::toString).toList()),
                        "-PoutputDir=" + mergedMetadataDirectory
                ),
                "Cannot merge generated metadata",
                layout.getProjectDirectory().getAsFile().toPath()
        );
        Path mergedMetadataFile = mergedMetadataDirectory.resolve("reachability-metadata.json");
        if (!Files.isRegularFile(mergedMetadataFile)) {
            throw new RuntimeException("Merged metadata file was not created: " + mergedMetadataFile);
        }
        Files.createDirectories(durableMetadataDirectory);
        Files.copy(mergedMetadataFile, durableMetadataDirectory.resolve("reachability-metadata.json"), StandardCopyOption.REPLACE_EXISTING);
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(path)) {
            for (Path currentPath : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(currentPath);
            }
        }
    }

}
