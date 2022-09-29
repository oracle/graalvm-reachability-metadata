/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package org.graalvm.internal.tck.harness.tasks

import groovy.transform.Internal
import org.graalvm.internal.common.MetadataDescriptor
import org.graalvm.internal.tck.harness.MetadataLookupLogic
import org.gradle.api.tasks.Exec

import javax.inject.Inject
import java.nio.file.Path
import java.util.stream.Collectors

import static groovy.io.FileType.FILES
import static org.graalvm.internal.tck.RepoScanner.tckRoot

/**
 * Abstract task that is used to invoke test subprojects.
 */
@SuppressWarnings("unused")
abstract class AbstractSubprojectTask extends Exec {

    @Inject
    AbstractSubprojectTask(MetadataDescriptor metadataDescriptor, List<String> cmd) {
        Path metadataDir = metadataDescriptor.getMetadataDir()
        Path testDir = metadataDescriptor.getTestDir()

        Map<String, String> env = new HashMap<>(System.getenv())
        // Environment variables for setting up TCK
        env.put("GVM_TCK_LC", metadataDescriptor.getGAVCoordinates())
        env.put("GVM_TCK_EXCLUDE", metadataDescriptor.getOverride().toString())
        env.put("GVM_TCK_LV", metadataDescriptor.getVersion())
        env.put("GVM_TCK_MD", metadataDir.toAbsolutePath().toString())
        env.put("GVM_TCK_TCKDIR", tckRoot.toAbsolutePath().toString())
        environment(env)

        commandLine(cmd)
        workingDir(testDir.toAbsolutePath().toFile())

        def (inputs, outputs) = getInputsOutputs(testDir)
        getInputs().files(MetadataLookupLogic.getMetadataFileList(metadataDir))
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
