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
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Routes foreign-condition metadata and regenerates affected owner statistics
 * after {@code checkMetadataFiles} fails.
 * §FS-metadata
 */
@SuppressWarnings("unused")
public abstract class RouteForeignMetadataTask extends CoordinatesAwareTask {
    private static final String REACHABILITY_METADATA_FILE = "reachability-metadata.json";
    private static final String DEPENDENCY_PREFIX = "TCK_RUNTIME_DEPENDENCY=";

    private final ObjectMapper objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    @Inject
    public abstract ExecOperations getExecOperations();

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
            deleteFailureReport(coordinate);
            try {
                routeMetadata(coordinate);
            } catch (UnsupportedMetadataOwnerException exception) {
                writeFailureReport(coordinate, exception);
                failures.add(coordinate + ": " + exception.getMessage());
                getLogger().error("routeForeignMetadata failed for {}: {}", coordinate, exception.getMessage());
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
                libraryMetadata,
                resolveDependencyArtifacts(parsedCoordinates)
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

    protected List<ResolvedDependencyArtifact> resolveDependencyArtifacts(Coordinates coordinates) {
        Path repositoryRoot = tckExtension.getRepoRoot().get().getAsFile().toPath();
        Path testDirectory = tckExtension.getTestDir(coordinates.toString());
        Path metadataDirectory = tckExtension.getMetadataDir(coordinates.toString());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Map<String, String> environment = new HashMap<>(System.getenv());
        environment.put("GVM_TCK_LC", coordinates.toString());
        environment.put("GVM_TCK_LV", coordinates.version());
        environment.put("GVM_TCK_EXCLUDE", "false");
        environment.put("GVM_TCK_MD", metadataDirectory.toAbsolutePath().toString());
        environment.put("GVM_TCK_TCKDIR", tckExtension.getTckRoot().get().getAsFile().toPath().toString());

        int exitCode = getExecOperations().exec(spec -> {
            spec.commandLine(repositoryRoot.resolve("gradlew").toString(), "--quiet", "listRuntimeDependencyArtifacts");
            spec.workingDir(testDirectory.toFile());
            spec.environment(environment);
            spec.setStandardOutput(output);
            spec.setErrorOutput(output);
            spec.setIgnoreExitValue(true);
        }).getExitValue();
        String capturedOutput = output.toString(StandardCharsets.UTF_8);
        if (exitCode != 0) {
            throw new GradleException(
                    "Cannot resolve runtime dependencies for " + coordinates + ":\n" + capturedOutput.strip()
            );
        }

        List<ResolvedDependencyArtifact> artifacts = new ArrayList<>();
        for (String line : capturedOutput.split("\\R")) {
            if (!line.startsWith(DEPENDENCY_PREFIX)) {
                continue;
            }
            try {
                ObjectNode artifact = objectMapper.readTree(line.substring(DEPENDENCY_PREFIX.length()))
                        instanceof ObjectNode objectNode ? objectNode : null;
                if (artifact == null || !artifact.hasNonNull("coordinate") || !artifact.hasNonNull("file")) {
                    throw new GradleException("Invalid resolved dependency artifact record: " + line);
                }
                artifacts.add(new ResolvedDependencyArtifact(
                        artifact.get("coordinate").asText(),
                        Path.of(artifact.get("file").asText())
                ));
            } catch (IOException exception) {
                throw new GradleException("Cannot parse resolved dependency artifact record: " + line, exception);
            }
        }
        if (artifacts.isEmpty()) {
            throw new GradleException("No runtime dependency JARs resolved for " + coordinates);
        }
        return artifacts;
    }

    private void deleteFailureReport(String coordinate) {
        try {
            Files.deleteIfExists(failureReportPath(coordinate));
        } catch (IOException exception) {
            throw new GradleException("Cannot delete stale routeForeignMetadata report for " + coordinate, exception);
        }
    }

    private void writeFailureReport(String sourceCoordinate, UnsupportedMetadataOwnerException exception) {
        ObjectNode report = objectMapper.createObjectNode();
        report.put("reason", exception.reason());
        report.put("coordinate", exception.coordinate());
        try {
            Path reportPath = failureReportPath(sourceCoordinate);
            Files.createDirectories(reportPath.getParent());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(reportPath.toFile(), report);
        } catch (IOException ioException) {
            throw new GradleException("Cannot write routeForeignMetadata failure report", ioException);
        }
    }

    private Path failureReportPath(String coordinate) {
        return getProject().getLayout().getBuildDirectory()
                .file("reports/route-foreign-metadata/" + coordinate.replace(':', '_') + ".json")
                .get().getAsFile().toPath();
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
