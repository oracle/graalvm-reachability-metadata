/*
 * Licensed under Public Domain (CC0)
 *
 * To the extent possible under law, the person who associated CC0 with
 * this code has waived all copyright and related or neighboring
 * rights to this code.
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package org.graalvm.internal.tck.harness.tasks


import org.graalvm.internal.tck.harness.MetadataLookupLogic
import org.graalvm.internal.tck.harness.TestLookupLogic
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

import javax.inject.Inject
import java.nio.file.Path
import java.util.stream.Collectors

import static org.graalvm.internal.tck.Utils.readIndexFile
import static org.graalvm.internal.tck.Utils.splitCoordinates

/**
 * Task that is used to start subproject tests.
 */
@SuppressWarnings("unused")
abstract class TestInvocationTask extends AbstractSubprojectTask {

    static final DEFAULT_ARGS = List.of("gradle", "nativeTest")

    @Input
    String coordinates

    @Inject
    TestInvocationTask(String coordinates) {
        super(coordinates, getArguments(coordinates))
        dependsOn("check")
        this.coordinates = coordinates
    }

    /**
     * Fetches arguments for test invocation from index.json file (if present).
     * @param coordinates
     * @return list of processed arguments
     */
    static List<String> getArguments(String coordinates) {
        try {
            Map<String, List<String>> testIndex = readIndexFile(TestLookupLogic.getTestDir(coordinates)) as Map<String, List<String>>
            if (!testIndex.containsKey("test-command")) {
                return DEFAULT_ARGS
            }

            Path metadataDir = MetadataLookupLogic.getMetadataDir(coordinates)
            return testIndex.get("test-command").stream()
                    .map(c -> processCommand(c, metadataDir, coordinates))
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
    static String processCommand(String cmd, Path metadataDir, String coordinates) {
        def (String groupId, String artifactId, String version) = splitCoordinates(coordinates)
        return cmd.replace("<metadata_dir>", metadataDir.toAbsolutePath().toString())
                .replace("<group_id>", groupId)
                .replace("<artifact_id>", artifactId)
                .replace("<version>", version)
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
