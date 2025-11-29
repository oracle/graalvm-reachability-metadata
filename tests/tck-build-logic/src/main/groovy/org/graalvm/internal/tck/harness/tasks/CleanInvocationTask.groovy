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
abstract class CleanInvocationTask extends AllCoordinatesExecTask {


    @Override
    List<String> commandFor(String coordinates) {
        return [tckExtension.repoRoot.get().asFile.toPath().resolve("gradlew").toString(), "clean"]
    }

    @Override
    protected String errorMessageFor(String coordinates, int exitCode) {
        "Clean task failed"
    }

}
