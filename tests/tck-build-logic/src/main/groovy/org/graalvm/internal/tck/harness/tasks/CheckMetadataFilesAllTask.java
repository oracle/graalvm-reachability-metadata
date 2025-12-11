/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness.tasks;

import org.gradle.api.GradleException;
import org.gradle.api.tasks.TaskAction;
import org.graalvm.internal.tck.MetadataFilesCheckerTask;

import java.util.ArrayList;
import java.util.List;

/**
 * Executes MetadataFilesCheckerTask for all matching coordinates resolved via -Pcoordinates.
 * This unifies handling so the task itself performs coordinate resolution and iteration.
 */
@SuppressWarnings("unused")
public abstract class CheckMetadataFilesAllTask extends CoordinatesAwareTask {

    @TaskAction
    public void runAll() {
        List<String> coords = resolveCoordinates();
        if (coords.isEmpty()) {
            getLogger().lifecycle("No matching coordinates found for metadata checks. Nothing to do.");
            return;
        }

        List<String> failures = new ArrayList<>();
        for (String c : coords) {
            if (c.startsWith("samples:") || c.startsWith("org.example:")) {
                continue; // skip samples/infrastructure
            }
            String tmpName = "checkMetadataFiles_" + c.replace(":", "_") + "_" + System.nanoTime();
            MetadataFilesCheckerTask t = getProject().getTasks().create(tmpName, MetadataFilesCheckerTask.class);
            t.setCoordinates(c);
            try {
                t.run();
                getLogger().lifecycle("Metadata files check passed for {}", c);
            } catch (Throwable ex) {
                failures.add(c + ": " + ex.getMessage());
                getLogger().error("Metadata files check failed for {}: {}", c, ex.getMessage());
            } finally {
                // Best effort cleanup to avoid cluttering the task graph
                t.setEnabled(false);
            }
        }

        if (!failures.isEmpty()) {
            String msg = "Metadata files check failed for the following coordinates:\n - " + String.join("\n - ", failures);
            throw new GradleException(msg);
        }
    }
}
