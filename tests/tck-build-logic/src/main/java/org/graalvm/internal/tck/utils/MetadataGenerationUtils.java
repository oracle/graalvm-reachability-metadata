package org.graalvm.internal.tck.utils;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.graalvm.internal.tck.CoordinateUtils;
import org.gradle.api.file.ProjectLayout;
import org.gradle.process.ExecOperations;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

/**
 * Static utility class for operations shared by ContributionTask and GenerateMetadataTask.
 */
public final class MetadataGenerationUtils {

    public static final String BUILD_FILE = "build.gradle";
    private static final String USER_CODE_FILTER_FILE = "user-code-filter.json";

    private static final ObjectMapper objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private MetadataGenerationUtils() {
        // utility
    }

    public static Path getPathFromProject(ProjectLayout layout, String fileName) {
        return layout.getProjectDirectory().file(fileName).getAsFile().toPath();
    }

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

    public static void addAgentConfigBlock(Path testsDirectory) throws IOException {
        Path buildFilePath = testsDirectory.resolve(BUILD_FILE);
        InteractiveTaskUtils.printUserInfo("Configuring agent block in: " + BUILD_FILE);

        if (!Files.isRegularFile(buildFilePath)) {
            throw new RuntimeException("Cannot add agent block to " + buildFilePath + ". Please check if a " + BUILD_FILE + " exists on that location.");
        }

        try (InputStream stream = MetadataGenerationUtils.class.getResourceAsStream("/contributing/agent.template")) {
            if (stream == null) {
                throw new RuntimeException("Cannot find template for the graalvm configuration block");
            }

            String content = System.lineSeparator() + (new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            writeToFile(buildFilePath, content, StandardOpenOption.APPEND);
        }
    }

    public static void collectMetadata(ExecOperations execOps, Path testsDirectory, ProjectLayout layout,  String coordinates, Path gradlew) {
        Path metadataDirectory = MetadataGenerationUtils.computeMetadataDirectory(layout, coordinates);

        InteractiveTaskUtils.printUserInfo("Generating metadata");
        invokeCommand(execOps, gradlew.toString(), List.of("-Pagent", "test"), "Cannot generate metadata", testsDirectory);

        InteractiveTaskUtils.printUserInfo("Performing metadata copy");
        invokeCommand(execOps, gradlew + " metadataCopy --task test --dir " + metadataDirectory, "Cannot perform metadata copy", testsDirectory);
    }

    public static void writeToFile(Path path, String content, StandardOpenOption writeOption) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8, writeOption);
    }

    public static void invokeCommand(ExecOperations execOps, String command, String errorMessage) {
        invokeCommand(execOps, command, errorMessage, null);
    }

    public static void invokeCommand(ExecOperations execOps, String command, String errorMessage, Path workingDirectory) {
        String[] commandParts = command.split(" ");
        String executable = commandParts[0];

        List<String> args = List.of(Arrays.copyOfRange(commandParts, 1, commandParts.length));
        invokeCommand(execOps, executable, args, errorMessage, workingDirectory);
    }

    public static void invokeCommand(ExecOperations execOps, String executable, List<String> args, String errorMessage, Path workingDirectory) {
        ByteArrayOutputStream execOutput = new ByteArrayOutputStream();
        var result = execOps.exec(execSpec -> {
            if (workingDirectory != null) {
                execSpec.setWorkingDir(workingDirectory);
            }
            execSpec.setExecutable(executable);
            execSpec.setArgs(args);
            execSpec.setStandardOutput(execOutput);
        });

        if (result.getExitValue() != 0) {
            throw new RuntimeException(errorMessage + ". See: " + execOutput);
        }
    }

    public static Path computeTestsDirectory(ProjectLayout layout, String coordinates) {
        return getPathFromProject(layout, CoordinateUtils.replace("tests/src/$group$/$artifact$/$version$", CoordinateUtils.fromString(coordinates)));
    }

    public static Path computeMetadataDirectory(ProjectLayout layout, String coordinates) {
        return getPathFromProject(layout, CoordinateUtils.replace("metadata/$group$/$artifact$/$version$", CoordinateUtils.fromString(coordinates)));
    }
}
