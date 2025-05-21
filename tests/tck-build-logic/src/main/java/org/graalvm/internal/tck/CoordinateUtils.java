package org.graalvm.internal.tck;


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

    public static Coordinates fromString(String coordinates) {
        String[] coordinatesParts = coordinates.split(":");
        if (coordinatesParts.length != 3) {
            throw new IllegalArgumentException("Maven coordinates not provided in the correct format.");
        }

        String group = coordinatesParts[0];
        String artifact = coordinatesParts[1];
        String version = coordinatesParts[2];
        return new Coordinates(group, artifact, version);
    }

}
