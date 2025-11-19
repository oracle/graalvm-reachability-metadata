package org.graalvm.internal.tck;

import org.graalvm.internal.tck.utils.InteractiveTaskUtils;
import org.graalvm.internal.tck.utils.MetadataGenerationUtils;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.graalvm.internal.tck.model.MetadataVersionsIndexEntry;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.gradle.process.ExecOperations;
import org.gradle.api.GradleException;

import javax.inject.Inject;
import java.io.IOException;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

public abstract class FixTestNativeImageRun extends DefaultTask {
    private static final String GRADLEW = "gradlew";

    private String testLibraryCoordinates;
    private String newLibraryVersion;

    @Inject
    protected abstract ProjectLayout getLayout();

    @Inject
    protected abstract ExecOperations getExecOperations();

    @Option(option = "newLibraryVersion", description = "New version of library that is failing")
    public void setNewLibraryVersion(String newLibraryVersion) {
        this.newLibraryVersion = newLibraryVersion;
    }

    @Input
    public String getNewLibraryVersion() {
        return newLibraryVersion;
    }

    @Option(option = "testLibraryCoordinates", description = "Coordinates in the form of group:artifact:version")
    public void setTestLibraryCoordinates(String testLibraryCoordinates) {
        this.testLibraryCoordinates = testLibraryCoordinates;
    }

    @Input
    public String getTestLibraryCoordinates() {
        return testLibraryCoordinates;
    }

    @TaskAction
    public void run() throws IOException {
        Path testsDirectory = MetadataGenerationUtils.computeTestsDirectory(getLayout(), testLibraryCoordinates);
        Path gradlewPath = MetadataGenerationUtils.getPathFromProject(getLayout(), GRADLEW);

        Coordinates baseCoords = Coordinates.parse(testLibraryCoordinates);
        String newCoordsString = baseCoords.group() + ":" + baseCoords.artifact() + ":" + newLibraryVersion;
        Coordinates newCoords = Coordinates.parse(newCoordsString);
        updateIndexJson(newCoords);

        Path metadataDirectory = MetadataGenerationUtils.computeMetadataDirectory(getLayout(), newCoordsString);
        Files.createDirectories(metadataDirectory);

        // Ensure the tests build.gradle has an agent block; if not, create user-code-filter.json and add the agent block.
        Path buildFilePath = testsDirectory.resolve("build.gradle");
        if (!Files.isRegularFile(buildFilePath)) {
            throw new RuntimeException("Cannot find tests build file at: " + buildFilePath);
        }
        String buildGradle = Files.readString(buildFilePath, java.nio.charset.StandardCharsets.UTF_8);
        boolean hasAgentBlock = Pattern.compile("(?s)\\bagent\\s*\\{").matcher(buildGradle).find();
        if (!hasAgentBlock) {
            MetadataGenerationUtils.addUserCodeFilterFile(testsDirectory, java.util.List.of(baseCoords.group()));
            MetadataGenerationUtils.addAgentConfigBlock(testsDirectory);
        }

        MetadataGenerationUtils.collectMetadata(getExecOperations(), testsDirectory, getLayout(), newCoordsString, gradlewPath, newLibraryVersion);

        MetadataGenerationUtils.createIndexJsonSpecificVersion(metadataDirectory);

        // At the end, attempt to run tests with the new library version.
        // If the build fails, it can be due agent's non-deterministic nature.
        InteractiveTaskUtils.printUserInfo("Running the test with updated metadata");
        var execOutput = new java.io.ByteArrayOutputStream();
        var testResult = runTestWithVersion(gradlewPath, testLibraryCoordinates, newLibraryVersion, execOutput);
        if (testResult.getExitValue() != 0) {
            InteractiveTaskUtils.printUserInfo("Test run failed, running agent again");
            MetadataGenerationUtils.collectMetadata(getExecOperations(), testsDirectory, getLayout(), newCoordsString, gradlewPath);
            testResult = runTestWithVersion(gradlewPath, testLibraryCoordinates, newLibraryVersion, execOutput);
            if (testResult.getExitValue() != 0) {
                throw new GradleException("Test run failed. See output:\n" + execOutput);
            }
        }
    }

    private org.gradle.process.ExecResult runTestWithVersion(Path gradlewPath, String coords, String version, java.io.ByteArrayOutputStream execOutput) {
        return getExecOperations().exec(execSpec -> {
            execSpec.setExecutable(gradlewPath.toString());
            execSpec.setArgs(java.util.List.of("clean", "test", "-Pcoordinates=" + coords));
            execSpec.environment("GVM_TCK_LV", version);
            execSpec.setStandardOutput(execOutput);
        });
    }

    private void updateIndexJson(Coordinates newCoords) throws IOException {
        String indexPathTemplate = "metadata/$group$/$artifact$/index.json";
        File indexFile = MetadataGenerationUtils.getPathFromProject(getLayout(), CoordinateUtils.replace(indexPathTemplate, newCoords)).toFile();

        ObjectMapper objectMapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);

        // Read existing entries if file exists, otherwise start fresh
        List<MetadataVersionsIndexEntry> entries = new ArrayList<>();
        if (indexFile.exists()) {
            entries = objectMapper.readValue(indexFile, new TypeReference<>() {});
        }

        // Remove 'latest' flag from any existing latest entry
        for (int i = 0; i < entries.size(); i++) {
            MetadataVersionsIndexEntry entry = entries.get(i);
            if (Boolean.TRUE.equals(entry.latest())) {
                entries.set(i, new MetadataVersionsIndexEntry(
                        null, // latest removed
                        entry.override(),
                        entry.module(),
                        entry.defaultFor(),
                        entry.metadataVersion(),
                        entry.testedVersions()
                ));
            }
        }

        // Add the new entry and mark it as latest
        String moduleName = newCoords.group() + ":" + newCoords.artifact();
        List<String> testedVersions = new ArrayList<>();
        testedVersions.add(newCoords.version());

        MetadataVersionsIndexEntry newEntry = new MetadataVersionsIndexEntry(
                Boolean.TRUE,
                null,
                moduleName,
                null,
                newCoords.version(),
                testedVersions
        );
        entries.add(newEntry);

        DefaultPrettyPrinter prettyPrinter = new DefaultPrettyPrinter();
        prettyPrinter.indentArraysWith(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE);
        String json = objectMapper.writer(prettyPrinter).writeValueAsString(entries);
        if (!json.endsWith(System.lineSeparator())) {
            json = json + System.lineSeparator();
        }
        Files.writeString(indexFile.toPath(), json, java.nio.charset.StandardCharsets.UTF_8);
    }
}
