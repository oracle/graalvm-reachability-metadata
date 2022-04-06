package org.graalvm.internal.tck

import groovy.transform.Internal
import org.gradle.api.GradleException
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

import javax.inject.Inject
import java.nio.file.Path
import java.util.stream.Collectors

import static groovy.io.FileType.FILES

@SuppressWarnings("unused")
class TestInvocationTask extends Exec {

    @Input
    String coordinates

    @Inject
    TestInvocationTask(Map<String, ?> inv) {
        this.coordinates = inv["coordinates"]
        Path metadataDir = (Path) inv["metadata-directory"]
        Path testDir = (Path) inv["test-directory"]

        Map<String, String> env = new HashMap<>(System.getenv())
        if (inv.containsKey("test-environment") && inv["test-environment"] != null) {
            env.putAll((Map<String, String>) inv["test-environment"])
        }

        dependsOn("check")
        commandLine(inv["test-command"])
        workingDir(testDir.toFile())
        environment(env)

        def (inputs, outputs) = getInputsOutputs(testDir)
        getInputs().files(TestInvocationLogic.getMetadataFileList(metadataDir))
        getInputs().files(inputs)
        getOutputs().files(outputs)

        setIgnoreExitValue(true)
    }

    @TaskAction
    @Override
    void exec() {
        getLogger().lifecycle("====================")
        getLogger().lifecycle("Testing library: {}", coordinates)
        getLogger().lifecycle("Command: {}", String.join(" ", getCommandLine()))
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

        def excludedSubdirs = excludedSubdirNames.stream()
                .map(name -> projectDir.resolve(name).toFile().getCanonicalPath() + File.separator)
                .collect(Collectors.toList())

        def outputSubdirs = outputSubdirNames.stream()
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
