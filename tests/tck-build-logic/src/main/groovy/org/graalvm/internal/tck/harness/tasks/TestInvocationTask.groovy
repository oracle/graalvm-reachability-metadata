/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness.tasks

import org.gradle.api.provider.ProviderFactory

import javax.inject.Inject
import java.nio.file.Path
import java.util.stream.Collectors

import static org.graalvm.internal.tck.Utils.readIndexFile
import static org.graalvm.internal.tck.Utils.splitCoordinates

/**
 * Task that is used to start subproject tests for matching coordinates.
 * Coordinate resolution is unified and handled by the base class.
 */
@SuppressWarnings("unused")
abstract class TestInvocationTask extends AllCoordinatesExecTask {

    @Inject
    abstract ProviderFactory getProviders()

    @Override
    List<String> commandFor(String coordinates) {
        def defaultArgs = [tckExtension.repoRoot.get().asFile.toPath().resolve("gradlew").toString(), "nativeTest"]
        def installPathsProperty = providers.environmentVariable("TCK_JDK_INSTALLATION_PATHS")
        if (installPathsProperty.isPresent()) {
            defaultArgs.addAll(
                    [
                            "-Porg.gradle.java.installations.auto-detect=false",
                            "-Porg.gradle.java.installations.paths=${installPathsProperty.get()}"
                    ]
            )
        }
        try {
            Map<String, List<String>> testIndex = readIndexFile(tckExtension.getTestDir(coordinates)) as Map<String, List<String>>
            if (!testIndex.containsKey("test-command")) {
                return defaultArgs
            }

            Path metadataDir = tckExtension.getMetadataDir(coordinates)
            return testIndex.get("test-command").stream()
                    .map(c -> processCommand(c, metadataDir, coordinates))
                    .collect(Collectors.toList())
        } catch (FileNotFoundException ignored) {
            return defaultArgs
        }
    }

    /**
     * Fills in template parameters in the command invocation.
     * Parameters are defined as <param_name> in cmd.
     *
     * @param cmd command line with parameters
     * @param metadataDir metadata directory location
     * @param coordinates
     * @return final command
     */
    static String processCommand(String cmd, Path metadataDir, String coordinates) {
        def (String groupId, String artifactId, String version) = splitCoordinates(coordinates)
        return cmd.replace("<metadata_dir>", metadataDir.toAbsolutePath().toString())
                .replace("<group_id>", groupId)
                .replace("<artifact_id>", artifactId)
                .replace("<version>", version)
    }

    @Override
    protected String errorMessageFor(String coordinates, int exitCode) {
        "Test for ${coordinates} failed with exit code ${exitCode}."
    }

    @Override
    protected void beforeEach(String coordinates, List<String> command) {
        getLogger().lifecycle("====================")
        getLogger().lifecycle("Testing library: {}", coordinates)
        getLogger().lifecycle("Command: `{}`", String.join(" ", command))
        getLogger().lifecycle("Executing test...")
        getLogger().lifecycle("-------")
    }

    @Override
    protected void afterEach(String coordinates) {
        getLogger().lifecycle("-------")
        getLogger().lifecycle("Test for {} passed.", coordinates)
        getLogger().lifecycle("====================")
    }
}
