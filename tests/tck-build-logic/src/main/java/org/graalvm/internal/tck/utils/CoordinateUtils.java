package org.graalvm.internal.tck.utils;

import org.graalvm.internal.tck.Coordinates;
import org.gradle.api.GradleException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class CoordinateUtils {
    public static String replace(String template, Coordinates coordinates) {
        return template
                .replace("$group$", coordinates.group())
                .replace("$sanitizedGroup$", coordinates.sanitizedGroup())
                .replace("$artifact$", coordinates.artifact())
                .replace("$sanitizedArtifact$", coordinates.sanitizedArtifact())
                .replace("$capitalizedSanitizedArtifact$", coordinates.capitalizedSanitizedArtifact())
                .replace("$version$", coordinates.version());
    }

    public static Coordinates fromString(String coordinates) throws IllegalArgumentException {
        String[] coordinatesParts = coordinates.split(":");
        if (coordinatesParts.length != 3) {
            throw new IllegalArgumentException("Maven coordinates not provided in the correct format.");
        }

        String group = coordinatesParts[0];
        String artifact = coordinatesParts[1];
        String version = coordinatesParts[2];
        return new Coordinates(group, artifact, version);
    }

    // Fractional batching utilities moved from CoordinateUtils

    private static final Pattern FRACTIONAL = Pattern.compile("(\\d+)/(\\d+)");

    /**
     * Returns true if the provided string matches the fractional batch form "k/n".
     */
    public static boolean isFractionalBatch(String s) {
        if (s == null) return false;
        return FRACTIONAL.matcher(s).matches();
    }

    /**
     * Parses a fractional batch "k/n" into an int[]{k, n}.
     * Returns null if the string does not match the pattern.
     * Throws GradleException on invalid values.
     */
    public static int[] parseFraction(String s) {
        Matcher m = FRACTIONAL.matcher(s);
        if (!m.matches()) {
            return null;
        }
        int k = Integer.parseInt(m.group(1));
        int n = Integer.parseInt(m.group(2));
        if (k <= 0) {
            throw new GradleException("Cannot have a batch number less than 1");
        }
        if (n <= 0) {
            throw new GradleException("Cannot have a batch size less than 1");
        }
        if (k > n) {
            throw new GradleException("Cannot have a batch number larger than the batch size");
        }
        return new int[]{k, n};
    }

    /**
     * Given a list of coordinates, returns the k-th batch (1-based) out of n batches,
     * by sorting and selecting every n-th element starting from index (k-1).
     */
    public static List<String> computeBatchedCoordinates(List<String> coordinates, int index, int batches) {
        if (batches <= 0) {
            throw new GradleException("Invalid batches denominator: " + batches);
        }
        if (index < 1 || index > batches) {
            throw new GradleException("Invalid batch index: " + index + "/" + batches);
        }
        List<String> sorted = new ArrayList<>(coordinates);
        Collections.sort(sorted);
        int target = (index - 1);
        List<String> result = new ArrayList<>();
        int i = 0;
        for (String c : sorted) {
            if ((i % batches) == target) {
                result.add(c);
            }
            i++;
        }
        return result;
    }
}
