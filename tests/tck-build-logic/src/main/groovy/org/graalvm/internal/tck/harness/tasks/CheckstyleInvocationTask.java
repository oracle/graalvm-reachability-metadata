/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness.tasks;

import java.util.List;

/**
 * Task that is used to run checkstyle task on subprojects.
 */
@SuppressWarnings("unused")
public abstract class CheckstyleInvocationTask extends AllCoordinatesExecTask {

    @Override
    protected List<String> commandFor(String coordinates) {
        String gradlew = tckExtension.getRepoRoot().get().getAsFile().toPath().resolve("gradlew").toString();
        return java.util.List.of(gradlew, "checkstyle");
    }

    @Override
    protected String errorMessageFor(String coordinates, int exitCode) {
        return "Checkstyle failed";
    }
}
