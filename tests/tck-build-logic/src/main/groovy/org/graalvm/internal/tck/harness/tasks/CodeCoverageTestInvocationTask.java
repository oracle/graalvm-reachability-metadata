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
 * Task that runs the code-coverage-improvement extension suite ("codeCoverageTest")
 * on subprojects (§forge/WF-code-coverage-improvement.3.1).
 */
@SuppressWarnings("unused")
public abstract class CodeCoverageTestInvocationTask extends AllCoordinatesExecTask {

    @Override
    public List<String> commandFor(String coordinates) {
        List<String> command = new ArrayList<>(List.of(
                tckExtension.getRepoRoot().get().getAsFile().toPath().resolve("gradlew").toString(),
                "codeCoverageTest"
        ));
        appendProperty(command, "skipJacoco");
        return command;
    }

    @Override
    protected String errorMessageFor(String coordinates, int exitCode) {
        return "Code-coverage-improvement suite tests failed";
    }
}
