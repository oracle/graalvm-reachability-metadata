/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package org.graalvm.internal.tck.harness.tasks

import org.graalvm.internal.common.MetadataDescriptor
import org.graalvm.internal.tck.RepoScanner
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

import javax.inject.Inject
import java.nio.file.Path
import java.util.stream.Collectors

import static org.graalvm.internal.tck.Utils.readIndexFile

/**
 * Task that is used to start subproject tests.
 */
@SuppressWarnings("unused")
abstract class TestInvocationTask extends AbstractSubprojectTask {

    static final DEFAULT_ARGS = List.of(RepoScanner.repoRoot.resolve("gradlew").toString(), "nativeTest")

    @Input
    String coordinates

    @Inject
    TestInvocationTask(MetadataDescriptor metadataDescriptor) {
        super(metadataDescriptor, getArguments(metadataDescriptor))
        dependsOn("check")
        this.coordinates = metadataDescriptor.getGAVCoordinates()
    }

    /**
     * Fetches arguments for test invocation from index.json file (if present).
     * @param coordinates
     * @return list of processed arguments
     */
    static List<String> getArguments(MetadataDescriptor metadataDescriptor) {
        try {
            Map<String, List<String>> testIndex = readIndexFile(metadataDescriptor.getTestDir()) as Map<String, List<String>>
            if (!testIndex.containsKey("test-command")) {
                return DEFAULT_ARGS
            }

            Path metadataDir = metadataDescriptor.getMetadataDir()
            return testIndex.get("test-command").stream()
                    .map(c -> processCommand(c, metadataDir, metadataDescriptor))
                    .collect(Collectors.toList())
        } catch (FileNotFoundException ignored) {
            return DEFAULT_ARGS
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
    static String processCommand(String cmd, Path metadataDir, MetadataDescriptor metadataDescriptor) {
        return cmd.replace("<metadata_dir>", metadataDir.toAbsolutePath().toString())
                .replace("<group_id>", metadataDescriptor.getGroup())
                .replace("<artifact_id>", metadataDescriptor.getArtifact())
                .replace("<version>", metadataDescriptor.getVersion())
    }

    @TaskAction
    @Override
    void exec() {
        getLogger().lifecycle("====================")
        getLogger().lifecycle("Testing library: {}", coordinates)
        getLogger().lifecycle("Command: `{}`", String.join(" ", getCommandLine()))
        getLogger().lifecycle("Executing test...")
        getLogger().lifecycle("-------")

        super.exec()

        getLogger().lifecycle("-------")

        int exitCode = getExecutionResult().get().getExitValue()
        if (exitCode != 0) {
            throw new GradleException("Test for ${coordinates} failed with exit code ${exitCode}.")
        } else {
            getLogger().lifecycle("Test for {} passed.", coordinates)
            getLogger().lifecycle("====================")
        }
    }
}
