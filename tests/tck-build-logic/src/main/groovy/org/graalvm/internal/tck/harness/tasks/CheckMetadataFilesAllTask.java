/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness.tasks;

import org.gradle.api.GradleException;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;
import org.gradle.process.ExecResult;

import javax.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Executes metadata checks for all matching coordinates resolved via -Pcoordinates.
 * Instead of calling task internals directly, this task shells out to Gradle
 * to run 'checkMetadataFiles -Pcoordinates=<coord>' for each coordinate.
 */
@SuppressWarnings("unused")
public abstract class CheckMetadataFilesAllTask extends CoordinatesAwareTask {

    @Inject
    protected abstract ExecOperations getExecOperations();

    @TaskAction
    public void runAll() {
        List<String> coords = resolveCoordinates();
        if (coords.isEmpty()) {
            getLogger().lifecycle("No matching coordinates found for metadata checks. Nothing to do.");
            return;
        }

        List<String> failures = new ArrayList<>();
        String gradlew = tckExtension.getRepoRoot().get().getAsFile().toPath().resolve("gradlew").toString();

        for (String c : coords) {
            if (c.startsWith("samples:") || c.startsWith("org.example:")) {
                continue; // skip samples/infrastructure
            }

            List<String> cmd = java.util.List.of(gradlew, "checkMetadataFiles", "-Pcoordinates=" + c);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ByteArrayOutputStream err = new ByteArrayOutputStream();

            ExecResult res = getExecOperations().exec(spec -> {
                spec.commandLine(cmd);
                spec.workingDir(tckExtension.getRepoRoot().get().getAsFile());
                spec.setIgnoreExitValue(true);
                spec.setStandardOutput(new TeeOutputStream(out, System.out));
                spec.setErrorOutput(new TeeOutputStream(err, System.err));
            });

            int exit = res.getExitValue();
            if (exit != 0) {
                failures.add(c + ": exit " + exit + "\nstdout:\n" + out.toString(StandardCharsets.UTF_8) +
                        "\nstderr:\n" + err.toString(StandardCharsets.UTF_8));
                getLogger().error("Metadata files check failed for {} with exit {}", c, exit);
            } else {
                getLogger().lifecycle("Metadata files check passed for {}", c);
            }
        }

        if (!failures.isEmpty()) {
            String msg = "Metadata files check failed for the following coordinates:\n - " +
                    String.join("\n - ", failures);
            throw new GradleException(msg);
        }
    }
}
