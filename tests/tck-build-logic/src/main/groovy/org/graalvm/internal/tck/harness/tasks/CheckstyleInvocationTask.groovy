/*
 * Licensed under Public Domain (CC0)
 *
 * To the extent possible under law, the person who associated CC0 with
 * this code has waived all copyright and related or neighboring
 * rights to this code.
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package org.graalvm.internal.tck.harness.tasks

import javax.inject.Inject

/**
 * Task that is used to run checkstyle task on subprojects.
 */
@SuppressWarnings("unused")
abstract class CheckstyleInvocationTask extends AbstractSubprojectTask {

    static final CHECKSTYLE_COMMAND = List.of("gradle", "checkstyle")

    @Inject
    CheckstyleInvocationTask(String coordinates, List<String> cmd) {
        super(coordinates, CHECKSTYLE_COMMAND)
    }
}
