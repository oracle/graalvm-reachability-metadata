package org.graalvm.internal.tck;

import java.util.Set;

/**
 * Dependency coordinates in the form 'group:artifact:version'.
 */
record Coordinates(String group, String artifact, String version) {
    private static final Set<Character> FORBIDDEN_CHARS = Set.of(
            ':', '-', '.'
    );

    Coordinates {
        if (group == null || group.isEmpty()) {
            throw new IllegalArgumentException("group must not be empty");
        }
        if (artifact == null || artifact.isEmpty()) {
            throw new IllegalArgumentException("artifact must not be empty");
        }
        if (version == null || version.isEmpty()) {
            throw new IllegalArgumentException("version must not be empty");
        }
    }

    String sanitizedGroup() {
        return sanitize(group);
    }

    String sanitizedArtifact() {
        return sanitize(artifact);
    }

    String capitalizedSanitizedArtifact() {
        String sanitizedArtifact = sanitizedArtifact();
        if (sanitizedArtifact.isEmpty()) {
            return sanitizedArtifact;
        }
        return sanitizedArtifact.substring(0, 1).toUpperCase() + sanitizedArtifact.substring(1);
    }

    @Override
    public String toString() {
        return group + ":" + artifact + ":" + version;
    }

    private String sanitize(String input) {
        StringBuilder stringBuilder = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (FORBIDDEN_CHARS.contains(c)) {
                stringBuilder.append('_');
            } else {
                stringBuilder.append(c);
            }
        }
        return stringBuilder.toString();
    }

    public static Coordinates parse(String input) {
        String[] parts = input.split(":", 3);
        if (parts.length != 3) {
            throw new IllegalArgumentException("coordinates must be in format <group>:<artifact>:<version>");
        }

        return new Coordinates(parts[0], parts[1], parts[2]);
    }
}
