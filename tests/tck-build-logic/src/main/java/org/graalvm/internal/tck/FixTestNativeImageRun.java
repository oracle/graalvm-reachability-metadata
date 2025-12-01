/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck;

import org.graalvm.internal.tck.utils.GeneralUtils;
import org.graalvm.internal.tck.utils.MetadataGenerationUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.gradle.process.ExecOperations;
import org.gradle.api.GradleException;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Gradle task to fix test failures during native-image runs.
 * It regenerates reachability metadata for the specified library version and updates the
 * module's index.json, then re-runs the tests.
 *
 * Properties:
 *  -PtestLibraryCoordinates: Coordinates in the form group:artifact:version
 *  -PnewLibraryVersion: New version of the library that is failing
 */
public abstract class FixTestNativeImageRun extends DefaultTask {
    private static final String GRADLEW = "gradlew";

    private String testLibraryCoordinates;
    private String newLibraryVersion;

    {
        Object testCoordsProp = getProject().findProperty("testLibraryCoordinates");
        if ((this.testLibraryCoordinates == null || this.testLibraryCoordinates.isBlank()) && testCoordsProp != null) {
            this.testLibraryCoordinates = testCoordsProp.toString();
        }
        Object newCoordsProp = getProject().findProperty("newLibraryVersion");
        if ((this.newLibraryVersion == null || this.newLibraryVersion.isBlank()) && newCoordsProp != null) {
            this.newLibraryVersion = newCoordsProp.toString();
        }
    }

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
        Path testsDirectory = GeneralUtils.computeTestsDirectory(getLayout(), testLibraryCoordinates);
        Path gradlewPath = GeneralUtils.getPathFromProject(getLayout(), GRADLEW);

        Coordinates baseCoords = Coordinates.parse(testLibraryCoordinates);
        String newCoordsString = baseCoords.group() + ":" + baseCoords.artifact() + ":" + newLibraryVersion;
        Coordinates newCoords = Coordinates.parse(newCoordsString);
        MetadataGenerationUtils.makeVersionLatestInIndexJson(getLayout(), newCoords);

        Path metadataDirectory = GeneralUtils.computeMetadataDirectory(getLayout(), newCoordsString);
        Files.createDirectories(metadataDirectory);

        // Ensure the tests build.gradle has an agent block; if not, create user-code-filter.json and add the agent block.
        Path buildFilePath = testsDirectory.resolve("build.gradle");
        if (!Files.isRegularFile(buildFilePath)) {
            throw new GradleException("Cannot find tests build file at: " + buildFilePath);
        }
        String buildGradle = Files.readString(buildFilePath, java.nio.charset.StandardCharsets.UTF_8);
        boolean hasAgentBlock = Pattern.compile("(?s)\\bagent\\s*\\{").matcher(buildGradle).find();
        if (!hasAgentBlock) {
            MetadataGenerationUtils.addUserCodeFilterFile(testsDirectory, List.of(baseCoords.group()));
            MetadataGenerationUtils.addAgentConfigBlock(testsDirectory);
        }

        // Update tests/src/index.json: add newLibraryVersion to the versions of the module under the test project path
        MetadataGenerationUtils.addNewVersionToTestsIndex(getLayout(), baseCoords, newLibraryVersion);

        MetadataGenerationUtils.collectMetadata(getExecOperations(), testsDirectory, getLayout(), newCoordsString, gradlewPath, newLibraryVersion);

        MetadataGenerationUtils.createIndexJsonSpecificVersion(metadataDirectory);

        // At the end, attempt to run tests with the new library version.
        // If the build fails, it can be due agent's non-deterministic nature.
        GeneralUtils.printInfo("Running the test with updated metadata");
        var execOutput = new java.io.ByteArrayOutputStream();
        var testResult = runTestWithVersion(gradlewPath, testLibraryCoordinates, newLibraryVersion, execOutput);
        if (testResult.getExitValue() != 0) {
            GeneralUtils.printInfo("Test run failed, running agent again");
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
            execSpec.setArgs(List.of("test", "-Pcoordinates=" + coords));
            execSpec.environment("GVM_TCK_LV", version);
            execSpec.setStandardOutput(execOutput);
        });
    }
}
