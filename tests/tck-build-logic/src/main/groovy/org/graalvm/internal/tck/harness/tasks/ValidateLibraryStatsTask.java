/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness.tasks;

import org.gradle.api.tasks.TaskAction;

/**
 * Validates committed mirrored library stats files.
 * <p>
 * Implements §TCK-test-harness.2 — the {@code validateLibraryStats} gate, including the Forge
 * run records (§forge/FS-forge-run-metrics).
 */
@SuppressWarnings("unused")
public abstract class ValidateLibraryStatsTask extends AbstractLibraryStatsTask {

    @TaskAction
    public void validate() {
        validateCommittedStatsFiles();
    }
}
