/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package org.graalvm.internal.tck.harness.tasks

import groovy.transform.Internal
import org.graalvm.internal.tck.harness.TckExtension
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.process.ExecSpec

import javax.inject.Inject
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.stream.Collectors
import static org.graalvm.internal.tck.Utils.splitCoordinates;
import static org.graalvm.internal.tck.Utils.readIndexFile;
import static org.graalvm.internal.tck.Utils.coordinatesMatch;

import static groovy.io.FileType.FILES

/**
 * Abstract task that is used to invoke test subprojects.
 */
@SuppressWarnings("unused")
abstract class AbstractSubprojectTask extends DefaultTask {

    protected final TckExtension tckExtension
    private final String coordinates

    @Inject
    abstract ExecOperations getExecOperations()

    @Input
    abstract List<String> getCommand();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    final Set<File> getInputFiles() {
        def inputFiles = project.objects.fileCollection()
        def metadataDir = tckExtension.getMetadataDir(coordinates)
        Path testDir = tckExtension.getTestDir(coordinates)
        inputFiles.from(project.files(tckExtension.getMetadataFileList(metadataDir)))
        def io = inputsFor(testDir)
        def result = inputFiles.from(io).files
        result
    }

    @OutputFile
    final File getOutputFile() {
        String hash = command.join(",").md5()
        def file = project.layout.buildDirectory.file("tests/${coordinates}/${hash}.out").get().asFile
        file
    }

    @Inject
    AbstractSubprojectTask(String coordinates) {
        this.tckExtension = project.extensions.findByType(TckExtension)
        this.coordinates = coordinates
    }


    protected final configureSpec(ExecSpec spec) {

        def (String groupId, String artifactId, String version) = splitCoordinates(coordinates)
        Path metadataDir = tckExtension.getMetadataDir(coordinates)
        boolean override = false

        def metadataIndex = readIndexFile(metadataDir.parent)
        for (def entry in metadataIndex) {
            if (coordinatesMatch((String) entry["module"], groupId, artifactId) && ((List<String>) entry["tested-versions"]).contains(version)) {
                if (entry.containsKey("override")) {
                    override |= entry["override"] as boolean
                }
                break
            }
        }

        Path testDir = tckExtension.getTestDir(coordinates)

        Map<String, String> env = new HashMap<>(System.getenv())
        // Environment variables for setting up TCK
        env.put("GVM_TCK_LC", coordinates)
        env.put("GVM_TCK_EXCLUDE", override.toString())
        if (System.getenv("GVM_TCK_LV") == null) {
            // we only set this env variable if user didn't specify it manually
            env.put("GVM_TCK_LV", version)
        }
        env.put("GVM_TCK_MD", metadataDir.toAbsolutePath().toString())
        env.put("GVM_TCK_TCKDIR", tckExtension.getTckRoot().get().getAsFile().toPath().toAbsolutePath().toString())
        spec.environment(env)
        spec.commandLine(getCommand())
        spec.workingDir(testDir.toAbsolutePath().toFile())

        spec.setIgnoreExitValue(true)
        spec.standardOutput = System.out
        spec.errorOutput = System.err
    }

    /**
     * Given project dir returns a tuple that contains a list of inputs.
     * @return lists of input files
     */
    @Internal
    def inputsFor(Path projectDir) {
        File dir = projectDir.toFile()
        def excludedSubdirNames = [".gradle", ".mvn"]

        List<String> excludedSubdirs = excludedSubdirNames.stream()
                .map(name -> projectDir.resolve(name).toFile().getCanonicalPath() + File.separator)
                .collect(Collectors.toList())


        def inputFiles = []

        dir.traverse(type: FILES) { File file ->
            if (excludedSubdirs.stream().noneMatch(curr -> file.getCanonicalPath().startsWith(curr))) {
                inputFiles.add(file.toPath())
            }
        }

        return Collections.unmodifiableList(inputFiles)
    }

    protected void beforeExecute() {
        // do nothing
    }

    protected void afterExecute() {
        // do nothing
    }

    protected String getErrorMessage(int exitCode) {
        "Execution of " + getCommand() + " failed."
    }

    @TaskAction
    final void execute() {
        beforeExecute()
        println "Command: $command"
        def out = new ByteArrayOutputStream()
        def err = new ByteArrayOutputStream()
        def execResult = execOperations.exec { spec ->
            configureSpec(spec)
            spec.standardOutput = new TeeOutputStream(out, System.out)
            spec.errorOutput = new TeeOutputStream(err, System.err)
        }
        outputFile.parentFile.mkdirs()
        outputFile.text = """Standard out
-----
${out.toString(StandardCharsets.UTF_8)}
-----
Standard err
----
${err.toString(StandardCharsets.UTF_8)}
----
"""
        def exitCode = execResult.exitValue
        if (exitCode != 0) {
            throw new GradleException(getErrorMessage(exitCode))
        }
        afterExecute()
    }
}
