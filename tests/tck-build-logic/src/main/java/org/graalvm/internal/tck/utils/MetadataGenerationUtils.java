package org.graalvm.internal.tck.utils;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.gradle.api.file.ProjectLayout;
import org.gradle.process.ExecOperations;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.regex.Pattern;

/**
 * Static utility class for operations shared by ContributionTask and GenerateMetadataTask.
 */
public final class MetadataGenerationUtils {

    public static final String BUILD_FILE = "build.gradle";
    private static final String USER_CODE_FILTER_FILE = "user-code-filter.json";

    private static final ObjectMapper objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private MetadataGenerationUtils() {
    }


    /**
     * Creates a user-code-filter.json file including the given packages (and excluding all others),
     * used to restrict metadata generation to user code.
     */
    public static void addUserCodeFilterFile(Path testsDirectory, List<String> packages) throws IOException {
        InteractiveTaskUtils.printUserInfo("Generating " + USER_CODE_FILTER_FILE);
        List<Map<String, String>> filterFileRules = new ArrayList<>();

        // add exclude classes
        filterFileRules.add(Map.of("excludeClasses", "**"));

        // add include classes
        packages.forEach(p -> filterFileRules.add(Map.of("includeClasses", p + ".**")));

        DefaultPrettyPrinter prettyPrinter = new DefaultPrettyPrinter();
        prettyPrinter.indentArraysWith(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE);
        File out = testsDirectory.resolve(USER_CODE_FILTER_FILE).toFile();
        objectMapper.writer(prettyPrinter).writeValue(out, Map.of("rules", filterFileRules));
    }

    /**
     * Appends the agent configuration block to the build.gradle in the tests directory
     * if it does not already exist.
     */
    public static void addAgentConfigBlock(Path testsDirectory) throws IOException {
        Path buildFilePath = testsDirectory.resolve(BUILD_FILE);
        InteractiveTaskUtils.printUserInfo("Configuring agent block in: " + BUILD_FILE);

        if (!Files.isRegularFile(buildFilePath)) {
            throw new RuntimeException("Cannot add agent block to " + buildFilePath + ". Please check if a " + BUILD_FILE + " exists on that location.");
        }

        // Skip generation if agent block already exists
        String buildGradle = Files.readString(buildFilePath, StandardCharsets.UTF_8);
        boolean hasAgentBlock = Pattern.compile("(?s)\\bagent\\s*\\{").matcher(buildGradle).find();
        if (hasAgentBlock) {
            InteractiveTaskUtils.printUserInfo("Agent block already present in: " + BUILD_FILE + " - skipping");
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

        InteractiveTaskUtils.printUserInfo("Generating metadata");
        GeneralUtils.invokeCommand(execOps, gradlew.toString(), List.of("-Pagent", "test"), "Cannot generate metadata", testsDirectory);

        InteractiveTaskUtils.printUserInfo("Performing metadata copy");
        GeneralUtils.invokeCommand(execOps, gradlew + " metadataCopy --task test --dir " + metadataDirectory, "Cannot perform metadata copy", testsDirectory);
    }
}
