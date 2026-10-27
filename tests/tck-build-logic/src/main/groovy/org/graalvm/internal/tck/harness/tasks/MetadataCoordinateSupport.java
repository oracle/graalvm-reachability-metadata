/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness.tasks;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.graalvm.internal.tck.Coordinates;
import org.graalvm.internal.tck.model.MetadataVersionsIndexEntry;
import org.graalvm.internal.tck.utils.CoordinateUtils;
import org.gradle.api.Project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Resolves the metadata bucket selected by an exact tested coordinate.
 * §FS-metadata
 */
final class MetadataCoordinateSupport {
    private MetadataCoordinateSupport() {
    }

    static Path resolveMetadataDirectory(
            Project project,
            ObjectMapper objectMapper,
            Coordinates coordinates
    ) throws IOException {
        Path conventional = project.file(
                CoordinateUtils.replace("metadata/$group$/$artifact$/$version$", coordinates)
        ).toPath();
        if (Files.isDirectory(conventional)) {
            return conventional;
        }

        Path artifactDirectory = conventional.getParent();
        Path indexFile = artifactDirectory.resolve("index.json");
        if (!Files.isRegularFile(indexFile)) {
            return null;
        }
        for (MetadataVersionsIndexEntry entry : readIndexEntries(objectMapper, indexFile)) {
            if (entry.testedVersions() != null && entry.testedVersions().contains(coordinates.version())) {
                Path sharedDirectory = artifactDirectory.resolve(entry.metadataVersion());
                return Files.isDirectory(sharedDirectory) ? sharedDirectory : null;
            }
        }
        return null;
    }

    static List<MetadataVersionsIndexEntry> readIndexEntries(ObjectMapper objectMapper, Path indexFile) throws IOException {
        return objectMapper.readValue(indexFile.toFile(), new TypeReference<>() {});
    }
}
