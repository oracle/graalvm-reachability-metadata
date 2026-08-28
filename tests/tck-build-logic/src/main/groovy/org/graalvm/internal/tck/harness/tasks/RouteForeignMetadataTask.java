/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness.tasks;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.graalvm.internal.tck.Coordinates;
import org.graalvm.internal.tck.MetadataFilesCheckerTask;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Routes foreign-condition metadata and regenerates affected owner statistics
 * after {@code checkMetadataFiles} fails.
 * §FS-metadata
 */
@SuppressWarnings("unused")
public abstract class RouteForeignMetadataTask extends CoordinatesAwareTask {
    private static final String REACHABILITY_METADATA_FILE = "reachability-metadata.json";

    private final ObjectMapper objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    @TaskAction
    public void run() {
        List<String> coordinates = resolveCoordinates();
        if (coordinates.isEmpty()) {
            getLogger().lifecycle("No matching coordinates found for routeForeignMetadata. Nothing to do.");
            return;
        }

        List<String> failures = new ArrayList<>();
        for (String coordinate : coordinates) {
            if (coordinate.startsWith("samples:") || coordinate.startsWith("org.example:")) {
                continue;
            }
            try {
                routeMetadata(coordinate);
            } catch (Exception exception) {
                failures.add(coordinate + ": " + exception.getMessage());
                getLogger().error("routeForeignMetadata failed for {}: {}", coordinate, exception.getMessage());
            }
        }

        if (!failures.isEmpty()) {
            throw new GradleException("routeForeignMetadata failed for the following coordinates:\n - "
                    + String.join("\n - ", failures));
        }
    }

    private void routeMetadata(String coordinate) throws IOException {
        Coordinates parsedCoordinates = Coordinates.parse(coordinate);
        Path metadataDirectory = MetadataCoordinateSupport.resolveMetadataDirectory(
                getProject(),
                objectMapper,
                parsedCoordinates
        );
        if (metadataDirectory == null) {
            getLogger().lifecycle(
                    "Skipping {}: no metadata bucket supports library version {}.",
                    coordinate,
                    parsedCoordinates.version()
            );
            return;
        }

        Path metadataFile = metadataDirectory.resolve(REACHABILITY_METADATA_FILE);
        if (!Files.isRegularFile(metadataFile)) {
            getLogger().lifecycle("Skipping {}: no {} found in {}", coordinate, REACHABILITY_METADATA_FILE, metadataDirectory);
            return;
        }

        ObjectNode libraryMetadata = objectMapper.readTree(metadataFile.toFile()) instanceof ObjectNode objectNode
                ? objectNode
                : null;
        if (libraryMetadata == null) {
            throw new GradleException("Expected object JSON in " + metadataFile);
        }

        MetadataOwnershipRouter ownershipRouter = new MetadataOwnershipRouter(objectMapper, getLogger());
        Set<String> relocatedOwners = ownershipRouter.route(
                getProject().file("metadata").toPath(),
                parsedCoordinates,
                libraryMetadata
        );
        if (relocatedOwners.isEmpty()) {
            getLogger().lifecycle("No foreign-condition metadata entries found for {}", coordinate);
            return;
        }

        validateRelocatedOwners(relocatedOwners);
        ownershipRouter.writeJson(metadataFile, libraryMetadata);
        generateRelocatedOwnerStats(relocatedOwners);
        getLogger().lifecycle("routeForeignMetadata completed for {}; relocated owners: {}", coordinate, relocatedOwners);
    }

    private void validateRelocatedOwners(Set<String> relocatedOwners) {
        for (String owner : relocatedOwners) {
            String taskName = "checkRelocatedMetadata_" + owner.replace(':', '_') + "_" + System.nanoTime();
            MetadataFilesCheckerTask checker = getProject().getTasks().create(taskName, MetadataFilesCheckerTask.class);
            checker.setCoordinates(owner);
            try {
                checker.run();
            } finally {
                checker.setEnabled(false);
            }
        }
    }

    protected void generateRelocatedOwnerStats(Set<String> relocatedOwners) {
        for (String owner : relocatedOwners) {
            String taskName = "generateRelocatedStats_" + owner.replace(':', '_') + "_" + System.nanoTime();
            GenerateLibraryStatsTask generator = getProject().getTasks().create(taskName, GenerateLibraryStatsTask.class);
            generator.setCoordinatesOverride(List.of(owner));
            try {
                generator.generate();
            } finally {
                generator.setEnabled(false);
            }
        }
    }
}
