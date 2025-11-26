/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package org.graalvm.internal.tck.harness.tasks

import org.gradle.api.tasks.Input

import javax.inject.Inject

@SuppressWarnings('unused')
abstract class CleanInvocationTask extends AbstractSubprojectTask {

    @Inject
    CleanInvocationTask(String coordinates) {
        super(coordinates)
    }

    @Override
    @Input
    List<String> getCommand() {
        return [tckExtension.repoRoot.get().asFile.toPath().resolve("gradlew").toString(), "clean"]
    }

    @Override
    protected String getErrorMessage(int exitCode) {
        "Clean task failed"
    }

}
