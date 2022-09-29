/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package org.graalvm.internal.tck.harness.tasks

import org.graalvm.internal.common.MetadataDescriptor
import org.graalvm.internal.tck.RepoScanner
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction

import javax.inject.Inject

/**
 * Task that is used to run checkstyle task on subprojects.
 */
@SuppressWarnings("unused")
abstract class CheckstyleInvocationTask extends AbstractSubprojectTask {

    static final CHECKSTYLE_COMMAND = List.of(RepoScanner.repoRoot.resolve("gradlew").toString(), "checkstyle")

    @Inject
    CheckstyleInvocationTask(MetadataDescriptor metadataDescriptor, List<String> cmd) {
        super(metadataDescriptor, CHECKSTYLE_COMMAND)
    }

    @TaskAction
    @Override
    void exec() {
        super.exec()
        int exitCode = getExecutionResult().get().getExitValue()
        if (exitCode != 0) {
            throw new GradleException("Checkstyle failed.")
        }
    }
}
