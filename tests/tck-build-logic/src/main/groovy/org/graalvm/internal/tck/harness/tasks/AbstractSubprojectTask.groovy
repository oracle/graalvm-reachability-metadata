/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package org.graalvm.internal.tck.harness.tasks

import groovy.transform.Internal
import org.graalvm.internal.tck.harness.MetadataLookupLogic
import org.graalvm.internal.tck.harness.TestLookupLogic
import org.gradle.api.tasks.Exec

import javax.inject.Inject
import java.nio.file.Path
import java.util.stream.Collectors

import static groovy.io.FileType.FILES
import static org.graalvm.internal.tck.TestUtils.tckRoot
import static org.graalvm.internal.tck.Utils.splitCoordinates

/**
 * Abstract task that is used to invoke test subprojects.
 */
@SuppressWarnings("unused")
abstract class AbstractSubprojectTask extends Exec {

    @Inject
    AbstractSubprojectTask(String coordinates, List<String> cmd) {
        def (String groupId, String artifactId, String version) = splitCoordinates(coordinates)
        List<Path> metadataDirs = MetadataLookupLogic.getMetadataDirs(coordinates)
        Path testDir = TestLookupLogic.getTestDir(coordinates)

        String metadataCp = String.join(":", metadataDirs.stream().map(m -> m.toAbsolutePath().toString()).toList())

        Map<String, String> env = new HashMap<>(System.getenv())
        // Environment variables for setting up TCK
        env.put("GVM_TCK_LV", version)
        env.put("GVM_TCK_MD", metadataCp)
        env.put("GVM_TCK_TCKDIR", tckRoot.toAbsolutePath().toString())
        environment(env)

        commandLine(cmd)
        workingDir(testDir.toAbsolutePath().toFile())

        def (inputs, outputs) = getInputsOutputs(testDir)
        metadataDirs.forEach(entry -> {
            getInputs().files(MetadataLookupLogic.getMetadataFileList(entry))
        })
        getInputs().files(inputs)
        getOutputs().files(outputs)

        setIgnoreExitValue(true)
    }

    /**
     * Given project dir returns a tuple that contains a list of inputs and a list of outputs.
     * @param projectDir
     * @return tuple containing lists of input and output files
     */
    @Internal
    def static getInputsOutputs(Path projectDir) {
        File dir = projectDir.toFile()
        def excludedSubdirNames = [".gradle", ".mvn"]
        def outputSubdirNames = ["build", "target", "bin"]

        List<String> excludedSubdirs = excludedSubdirNames.stream()
                .map(name -> projectDir.resolve(name).toFile().getCanonicalPath() + File.separator)
                .collect(Collectors.toList())

        List<String> outputSubdirs = outputSubdirNames.stream()
                .map(name -> projectDir.resolve(name).toFile().getCanonicalPath() + File.separator)
                .collect(Collectors.toList())


        def inputFiles = []
        def outputFiles = []

        dir.traverse(type: FILES) { File file ->
            if (outputSubdirs.stream().anyMatch(curr -> file.getCanonicalPath().startsWith(curr))) {
                outputFiles.add(file.toPath())
            } else if (excludedSubdirs.stream().noneMatch(curr -> file.getCanonicalPath().startsWith(curr))) {
                inputFiles.add(file.toPath())
            }
        }

        return [inputFiles, outputFiles]
    }
}
