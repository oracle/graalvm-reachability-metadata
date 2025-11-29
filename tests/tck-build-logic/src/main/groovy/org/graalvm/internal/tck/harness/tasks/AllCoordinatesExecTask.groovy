/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness.tasks

import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.process.ExecSpec
import org.graalvm.internal.tck.harness.TckExtension

import javax.inject.Inject
import java.nio.charset.StandardCharsets
import java.nio.file.Path

import static org.graalvm.internal.tck.Utils.coordinatesMatch
import static org.graalvm.internal.tck.Utils.readIndexFile
import static org.graalvm.internal.tck.Utils.splitCoordinates

/**
 * Base task that resolves coordinates (via CoordinatesAwareTask) and executes a command for each coordinate.
 * Subclasses implement commandFor(String coordinates) and may override hooks for logging.
 */
abstract class AllCoordinatesExecTask extends CoordinatesAwareTask {

    @Inject
    abstract ExecOperations getExecOperations()


    /**
     * Subclasses must return the command line to run for the given coordinates.
     */
    abstract List<String> commandFor(String coordinates)

    /**
     * Customize error message.
     */
    protected String errorMessageFor(String coordinates, int exitCode) {
        return "Execution failed for ${coordinates} with exit code ${exitCode}"
    }

    /**
     * Hook invoked before executing each coordinate.
     */
    protected void beforeEach(String coordinates, List<String> command) {
        // no-op
    }

    /**
     * Hook invoked after executing each coordinate on success.
     */
    protected void afterEach(String coordinates) {
        // no-op
    }

    @TaskAction
    final void runAll() {
        List<String> coords = resolveCoordinates()
        if (coords.isEmpty()) {
            getLogger().lifecycle("No matching coordinates found. Nothing to do.")
            return
        }
        for (String c : coords) {
            runSingle(c)
        }
    }

    private void runSingle(String coordinates) {
        List<String> command = commandFor(coordinates)
        beforeEach(coordinates, command)

        def out = new ByteArrayOutputStream()
        def err = new ByteArrayOutputStream()

        def execResult = getExecOperations().exec { ExecSpec spec ->
            this.configureSpec(spec, coordinates, command)
            spec.standardOutput = new TeeOutputStream(out, System.out)
            spec.errorOutput = new TeeOutputStream(err, System.err)
        }

        // write output file like AbstractSubprojectTask
        String hash = command.join(",").md5()
        File outputFile = project.layout.buildDirectory.file("tests/${coordinates}/${hash}.out").get().asFile
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

        int exitCode = execResult.exitValue
        if (exitCode != 0) {
            throw new GradleException(errorMessageFor(coordinates, exitCode))
        }
        afterEach(coordinates)
    }

    protected void configureSpec(ExecSpec spec, String coordinates, List<String> command) {
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
        env.put("GVM_TCK_LC", coordinates)
        env.put("GVM_TCK_EXCLUDE", override.toString())
        if (System.getenv("GVM_TCK_LV") == null) {
            env.put("GVM_TCK_LV", version)
        }
        env.put("GVM_TCK_MD", metadataDir.toAbsolutePath().toString())
        env.put("GVM_TCK_TCKDIR", tckExtension.getTckRoot().get().getAsFile().toPath().toAbsolutePath().toString())

        spec.environment(env)
        spec.commandLine(command)
        spec.workingDir(testDir.toAbsolutePath().toFile())
        spec.setIgnoreExitValue(true)
        spec.standardOutput = System.out
        spec.errorOutput = System.err
    }
}
