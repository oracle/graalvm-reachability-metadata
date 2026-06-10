/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness.tasks;

import java.util.ArrayList;
import java.util.List;

/**
 * Task that is used to run JVM tests (Gradle 'test') on subprojects.
 */
@SuppressWarnings("unused")
public abstract class JavaTestInvocationTask extends AllCoordinatesExecTask {

    @Override
    public List<String> commandFor(String coordinates) {
        List<String> command = new ArrayList<>(List.of(
                tckExtension.getRepoRoot().get().getAsFile().toPath().resolve("gradlew").toString(),
                "test"
        ));
        // §TCK-test-harness.3
        appendProperty(command, "skipJacoco");
        return command;
    }

    @Override
    protected String errorMessageFor(String coordinates, int exitCode) {
        return "Java tests failed";
    }
}
