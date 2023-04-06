/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package org.graalvm.internal.tck.harness.tasks

import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.Input

import javax.inject.Inject
import java.nio.file.Path
import java.util.stream.Collectors
import static org.graalvm.internal.tck.Utils.splitCoordinates;
import static org.graalvm.internal.tck.Utils.readIndexFile;

/**
 * Task that is used to start subproject tests.
 */
@SuppressWarnings("unused")
abstract class TestInvocationTask extends AbstractSubprojectTask {

    @Input
    String coordinates

    @Inject
    TestInvocationTask(String coordinates) {
        super(coordinates)
        def me = this
        project.tasks.named("check") {
            dependsOn(me)
        }
        this.coordinates = coordinates
    }

    @Inject
    abstract ProviderFactory getProviders();

    /**
     * Fetches arguments for test invocation from index.json file (if present).
     * @param coordinates
     * @return list of processed arguments
     */
    @Override
    @Input
    List<String> getCommand() {
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
    protected String getErrorMessage(int exitCode) {
        "Test for ${coordinates} failed with exit code ${exitCode}."
    }

    @Override
    protected void beforeExecute() {
        getLogger().lifecycle("====================")
        getLogger().lifecycle("Testing library: {}", coordinates)
        getLogger().lifecycle("Command: `{}`", String.join(" ", command))
        getLogger().lifecycle("Executing test...")
        getLogger().lifecycle("-------")
    }

    @Override
    protected void afterExecute() {
        getLogger().lifecycle("-------")
        getLogger().lifecycle("Test for {} passed.", coordinates)
        getLogger().lifecycle("====================")
    }

}
