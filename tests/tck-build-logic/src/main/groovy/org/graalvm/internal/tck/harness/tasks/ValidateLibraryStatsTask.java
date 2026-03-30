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
 */
@SuppressWarnings("unused")
public abstract class ValidateLibraryStatsTask extends AbstractLibraryStatsTask {

    @TaskAction
    public void validate() {
        validateCommittedStatsFiles();
    }
}
