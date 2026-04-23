/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.utils;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.graalvm.internal.tck.model.DiscoveredArtifactMetadata;
import org.gradle.api.file.ProjectLayout;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Utilities for build-local artifact metadata discovery files.
 */
public final class ArtifactMetadataDiscoveryUtils {
    private static final String DISCOVERY_DIR = "discovered-artifact-metadata";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private ArtifactMetadataDiscoveryUtils() {
    }

    public static Path discoveryFile(ProjectLayout layout, String coordinates) {
        return layout.getBuildDirectory().dir(DISCOVERY_DIR).get().getAsFile().toPath().resolve(sanitizeCoordinate(coordinates) + ".json");
    }

    public static void initializeDiscoveryFile(Path discoveryFile, String coordinates) throws IOException {
        if (Files.exists(discoveryFile)) {
            return;
        }
        Files.createDirectories(discoveryFile.getParent());
        writeDiscoveryFile(discoveryFile, new DiscoveredArtifactMetadata(
                coordinates,
                null,
                null,
                null,
                null,
                null,
                null
        ));
    }

    public static void writeDiscoveryFile(Path discoveryFile, DiscoveredArtifactMetadata metadata) throws IOException {
        Files.createDirectories(discoveryFile.getParent());
        String json = OBJECT_MAPPER.writeValueAsString(metadata);
        if (!json.endsWith(System.lineSeparator())) {
            json = json + System.lineSeparator();
        }
        Files.writeString(discoveryFile, json, StandardCharsets.UTF_8);
    }

    public static DiscoveredArtifactMetadata readDiscoveryFile(Path discoveryFile) throws IOException {
        return OBJECT_MAPPER.readValue(discoveryFile.toFile(), DiscoveredArtifactMetadata.class);
    }

    public static String sanitizeCoordinate(String coordinate) {
        return coordinate.replace(":", "-").replace("/", "-");
    }
}
