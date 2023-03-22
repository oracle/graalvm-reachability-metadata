package org.graalvm.internal.tck;

public class CoordinateUtils {

    public static String replace(String template, Coordinates coordinates) {
        return template
                .replace("$group$", coordinates.group())
                .replace("$sanitizedGroup$", coordinates.sanitizedGroup())
                .replace("$artifact$", coordinates.artifact())
                .replace("$sanitizedArtifact$", coordinates.sanitizedArtifact())
                .replace("$capitalizedSanitizedArtifact$", coordinates.capitalizedSanitizedArtifact())
                .replace("$version$", coordinates.version());
    }

}