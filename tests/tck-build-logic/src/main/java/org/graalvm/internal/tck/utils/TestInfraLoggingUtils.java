/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.utils;

import java.util.List;

/**
 * Utilities for printing visible reproducer commands in batch runs.
 */
public final class TestInfraLoggingUtils {

    public static final String DELIMITER = "================================================================================";

    private TestInfraLoggingUtils() {
    }

    /**
     * Builds a reproducer command for a single concrete coordinate.
     */
    public static String formatGradleCommand(
            String nativeImageMode,
            String gradleCommand,
            String taskName,
            String coordinateExpression,
            Integer parallelism
    ) {
        StringBuilder command = new StringBuilder();
        if (nativeImageMode != null
                && !nativeImageMode.isBlank()
                && !NativeImageConfigUtils.DEFAULT_MODE.equals(nativeImageMode)) {
            command.append("GVM_TCK_NATIVE_IMAGE_MODE=").append(nativeImageMode).append(' ');
        }
        String launcher = gradleCommand == null || gradleCommand.isBlank() ? "./gradlew" : gradleCommand;
        command.append(launcher)
                .append(' ')
                .append(taskName)
                .append(" -Pcoordinates=")
                .append(coordinateExpression)
                .append(parallelism == null ? "" : " -Pparallelism=" + parallelism)
                .append(" --stacktrace");
        return command.toString();
    }

    /**
     * Builds the `testInfra` reproducer command for a single concrete coordinate.
     */
    public static String formatTestInfraCommand(String nativeImageMode, String coordinate, int parallelism) {
        return formatGradleCommand(nativeImageMode, "./gradlew", "testInfra", coordinate, parallelism);
    }

    /**
     * Returns a delimiter-wrapped block for the per-library reproducer.
     */
    public static List<String> batchReproducerLines(
            String taskName,
            String nativeImageMode,
            String coordinate,
            Integer parallelism
    ) {
        return batchReproducerLines(taskName, "./gradlew", nativeImageMode, coordinate, parallelism);
    }

    /**
     * Returns a delimiter-wrapped block for the per-library reproducer.
     */
    public static List<String> batchReproducerLines(
            String taskName,
            String gradleCommand,
            String nativeImageMode,
            String coordinate,
            Integer parallelism
    ) {
        List<String> lines = new java.util.ArrayList<>();
        lines.add("COORDINATES: " + coordinate);
        lines.add(DELIMITER);
        lines.add("Task: " + taskName);
        if (nativeImageMode != null
                && !nativeImageMode.isBlank()
                && !NativeImageConfigUtils.DEFAULT_MODE.equals(nativeImageMode)) {
            lines.add("Native image mode: " + nativeImageMode);
        }
        lines.add("Reproducer command: " + formatGradleCommand(
                nativeImageMode,
                gradleCommand,
                taskName,
                "\"$COORDINATES\"",
                parallelism
        ));
        lines.add(DELIMITER);
        return List.copyOf(lines);
    }

}
