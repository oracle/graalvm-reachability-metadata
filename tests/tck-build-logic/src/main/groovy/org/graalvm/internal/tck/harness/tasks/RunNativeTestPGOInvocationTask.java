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
 * Task that runs the instrumented-PGO native test image once on matching
 * subprojects to collect the {@code .iprof} profile.
 * <p>
 * Supports the phase-6 PGO discovery analyzer of §WF-code-coverage-improvement.
 */
@SuppressWarnings("unused")
public abstract class RunNativeTestPGOInvocationTask extends AllCoordinatesExecTask {

    @Override
    public List<String> commandFor(String coordinates) {
        List<String> command = new ArrayList<>(List.of(
                tckExtension.getRepoRoot().get().getAsFile().toPath().resolve("gradlew").toString(),
                "runNativeTestPGO"
        ));
        appendProperty(command, "pgoProfilePath");
        return command;
    }

    @Override
    protected String errorMessageFor(String coordinates, int exitCode) {
        return "Instrumented PGO native image run failed for " + coordinates
                + " with exit code " + exitCode + ".";
    }
}
