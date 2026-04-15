/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.utils;

import org.gradle.api.GradleException;

import java.util.List;

/**
 * Utilities for printing visible `testInfra` reproducer commands in batch runs.
 */
public final class TestInfraLoggingUtils {

    public static final String DELIMITER = "================================================================================";

    private TestInfraLoggingUtils() {
    }

    /**
     * Builds the `testInfra` reproducer command for a single concrete coordinate.
     */
    public static String formatTestInfraCommand(String nativeImageMode, String coordinate, int parallelism) {
        StringBuilder command = new StringBuilder();
        if (nativeImageMode != null
                && !nativeImageMode.isBlank()
                && !NativeImageConfigUtils.DEFAULT_MODE.equals(nativeImageMode)) {
            command.append("GVM_TCK_NATIVE_IMAGE_MODE=").append(nativeImageMode).append(' ');
        }
        command.append("./gradlew testInfra -Pcoordinates=")
                .append(coordinate)
                .append(" -Pparallelism=")
                .append(parallelism)
                .append(" --stacktrace");
        return command.toString();
    }

    /**
     * Returns a delimiter-wrapped block for the per-library reproducer.
     */
    public static List<String> batchReproducerLines(String nativeImageMode, String coordinate, int parallelism) {
        return List.of(
                DELIMITER,
                "TESTINFRA REPRODUCER " + coordinate,
                formatTestInfraCommand(nativeImageMode, coordinate, parallelism),
                DELIMITER
        );
    }

    /**
     * Parses a positive parallelism value, falling back to the default used by `testInfra`.
     */
    public static int parseParallelism(String parallelismProperty) {
        if (parallelismProperty == null || parallelismProperty.isBlank()) {
            return 4;
        }
        try {
            int parsed = Integer.parseInt(parallelismProperty);
            if (parsed <= 0) {
                throw new GradleException("Invalid parallelism '" + parallelismProperty + "': must be >= 1.");
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new GradleException("Invalid parallelism '" + parallelismProperty + "': must be a positive integer.", e);
        }
    }
}
