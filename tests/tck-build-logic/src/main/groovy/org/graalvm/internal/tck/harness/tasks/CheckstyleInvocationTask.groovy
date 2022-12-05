/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package org.graalvm.internal.tck.harness.tasks

import org.gradle.api.tasks.Input

import javax.inject.Inject
/**
 * Task that is used to run checkstyle task on subprojects.
 */
@SuppressWarnings("unused")
abstract class CheckstyleInvocationTask extends AbstractSubprojectTask {

    static final CHECKSTYLE_COMMAND = List.of("gradle", "checkstyle")

    @Inject
    CheckstyleInvocationTask(String coordinates) {
        super(coordinates)
    }

    @Override
    @Input
    List<String> getCommand() {
        CHECKSTYLE_COMMAND
    }

    @Override
    protected String getErrorMessage(int exitCode) {
        "Checkstyle failed"
    }

}
