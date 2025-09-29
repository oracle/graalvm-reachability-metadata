/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.utils;

import org.gradle.api.file.ProjectLayout;
import org.gradle.process.ExecOperations;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * General utilities shared across TCK build logic.
 */
public final class GeneralUtils {

    private GeneralUtils() {
    }

    /**
     * Resolves and returns the Path of a file inside the Gradle project directory.
     */
    public static Path getPathFromProject(ProjectLayout layout, String fileName) {
        return layout.getProjectDirectory().file(fileName).getAsFile().toPath();
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

    /**
     * Executes the given executable with arguments in an optional working directory via Gradle ExecOperations.
     * Captures output and throws a RuntimeException if the exit code is non-zero.
     */
    public static void invokeCommand(ExecOperations execOps, String executable, List<String> args, String errorMessage, Path workingDirectory) {
        invokeCommand(execOps, executable, args, null, errorMessage, workingDirectory);
    }

    /**
     * Executes the given executable with arguments in an optional working directory via Gradle ExecOperations,
     * allowing custom environment variables.
     */
    public static void invokeCommand(ExecOperations execOps, String executable, List<String> args, Map<String, String> env, String errorMessage, Path workingDirectory) {
        ByteArrayOutputStream execOutput = new ByteArrayOutputStream();
        var result = execOps.exec(execSpec -> {
            if (workingDirectory != null) {
                execSpec.setWorkingDir(workingDirectory);
            }
            if (env != null && !env.isEmpty()) {
                execSpec.environment(env);
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

    public static void printInfo(String message) {
        ColoredOutput.println("[INFO] " + message + "...", ColoredOutput.OUTPUT_COLOR.BLUE);
    }

}
